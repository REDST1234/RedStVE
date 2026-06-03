import express from 'express';
import cors from 'cors';
import { bundle } from '@remotion/bundler';
import { renderMedia, selectComposition } from '@remotion/renderer';
import path from 'path';
import fs from 'fs';
import Redis from 'ioredis';
import dotenv from 'dotenv';
import { CompositionScriptSchema, type CompositionScript } from './schemas/CompositionScript';

// 加载 Java 后端的 .env 环境变量
dotenv.config({ path: path.join(process.cwd(), '../backend/.env') });

// 初始化 Redis 客户端
const redis = new Redis({
  host: process.env.REDIS_HOST || 'localhost',
  port: Number(process.env.REDIS_PORT || 6379),
  password: process.env.REDIS_PASSWORD || undefined,
  db: Number(process.env.REDIS_DATABASE || 0),
});

const app = express();
app.use(cors());
app.use(express.json({ limit: '50mb' }));

const resolveStorageDir = (): string => {
  const cwdStorage = path.join(process.cwd(), 'storage');
  if (fs.existsSync(cwdStorage)) {
    return cwdStorage;
  }
  const parentStorage = path.join(process.cwd(), '..', 'storage');
  if (fs.existsSync(parentStorage)) {
    return parentStorage;
  }
  return parentStorage;
};

// 托管 out 目录作为静态资源，方便前端直接播放生成好的视频
app.use('/out', express.static(path.join(process.cwd(), 'out')));
// 托管仓库 storage 目录，供 Remotion 在浏览器/Chromium 中访问本地素材与 BGM
app.use('/storage', express.static(resolveStorageDir()));

// 渲染任务存储 (简单内存版)
interface RenderTask {
  taskId: string;
  status: 'QUEUED' | 'RENDERING' | 'DONE' | 'FAILED';
  progress: number;
  outputPath: string | null;
  error: string | null;
}
const tasks = new Map<string, RenderTask>();

// 预先 bundle 以提高性能
let bundleLocation: string | null = null;
const BUNDLE_DIR = path.join(process.cwd(), 'out');
if (!fs.existsSync(BUNDLE_DIR)) fs.mkdirSync(BUNDLE_DIR, { recursive: true });

async function initBundler() {
  console.log('📦 Bundling Remotion project...');
  bundleLocation = await bundle({
    entryPoint: path.join(process.cwd(), 'src', 'index.ts'),
    webpackOverride: (config) => config,
  });
  console.log('✅ Bundle ready at:', bundleLocation);
}
initBundler();

app.post('/render', async (req, res) => {
  if (!bundleLocation) {
    return res.status(503).json({ error: 'Bundler is not ready yet' });
  }

  const body = (req.body ?? {}) as {
    taskId: string;
    compositionScript: CompositionScript;
  };
  const { taskId, compositionScript } = body;

  if (!taskId || !compositionScript) {
    return res.status(400).json({ error: 'taskId and compositionScript are required' });
  }

  const parsed = CompositionScriptSchema.safeParse(compositionScript);
  if (!parsed.success) {
    const errorMessage = `Invalid compositionScript: ${parsed.error.issues
      .map((issue) => `${issue.path.join('.') || '<root>'}: ${issue.message}`)
      .join('; ')}`;
    const failedTask: RenderTask = {
      taskId,
      status: 'FAILED',
      progress: 0,
      outputPath: null,
      error: errorMessage,
    };
    tasks.set(taskId, failedTask);
    await redis.setex(`render:task:${taskId}`, 3600, JSON.stringify(failedTask));
    return res.status(400).json(failedTask);
  }

  const taskData: RenderTask = {
    taskId,
    status: 'QUEUED',
    progress: 0,
    outputPath: null,
    error: null,
  };
  tasks.set(taskId, taskData);
  await redis.setex(`render:task:${taskId}`, 3600, JSON.stringify(taskData));

  // 异步渲染
  processRender(taskId, parsed.data, bundleLocation).catch(console.error);

  res.json({ taskId, status: 'QUEUED' });
});

app.get('/render/:taskId/status', async (req, res) => {
  const taskId = req.params.taskId;
  const task = tasks.get(taskId);
  if (task) {
    return res.json(task);
  }
  const redisTask = await redis.get(`render:task:${taskId}`);
  if (redisTask) {
    const parsedTask = JSON.parse(redisTask) as RenderTask;
    tasks.set(taskId, parsedTask);
    return res.json(parsedTask);
  }
  return res.status(404).json({ error: 'Task not found' });
});

async function processRender(taskId: string, script: CompositionScript, bundleUrl: string) {
  const task = tasks.get(taskId)!;
  task.status = 'RENDERING';
  
  try {
    const composition = await selectComposition({
      serveUrl: bundleUrl,
      id: 'DynamicVideo',
      inputProps: script,
    });

    const outputLocation = path.join(BUNDLE_DIR, `${taskId}.mp4`);

    await renderMedia({
      composition,
      serveUrl: bundleUrl,
      codec: 'h264',
      outputLocation,
      inputProps: script,
      onProgress: ({ progress }) => {
        task.progress = progress;
        // 高频更新 Redis 中的进度信息，前端通过轮询 Redis 获取进度
        redis.setex(`render:task:${taskId}`, 3600, JSON.stringify(task));
      },
    });

    task.status = 'DONE';
    task.outputPath = outputLocation;
    task.progress = 1;
    await redis.setex(`render:task:${taskId}`, 3600, JSON.stringify(task));
    console.log(`✅ Render complete: ${outputLocation}`);
  } catch (err: any) {
    task.status = 'FAILED';
    task.error = err.message;
    await redis.setex(`render:task:${taskId}`, 3600, JSON.stringify(task));
    console.error(`❌ Render failed for ${taskId}:`, err);
  }
}

const PORT = 3001;
app.listen(PORT, () => {
  console.log(`🚀 Remotion Render Service listening on port ${PORT}`);
});
