import express from 'express';
import cors from 'cors';
import { bundle } from '@remotion/bundler';
import { renderMedia, selectComposition, openBrowser } from '@remotion/renderer';
import path from 'path';
import fs from 'fs';
import Redis from 'ioredis';
import dotenv from 'dotenv';
import amqp from 'amqplib';
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

// 提前开启并复用无头浏览器实例，减少冷启动开销
let globalBrowserExecutable: string | null = null;

async function initBrowser() {
  console.log('🌍 Launching headless browser for reuse...');
  try {
    const browser = await openBrowser('chrome');
    globalBrowserExecutable = browser.browserExecutable;
    console.log(`✅ Browser ready. Executable: ${globalBrowserExecutable}`);
  } catch (err) {
    console.warn('⚠️ Failed to open reusable browser. Will fallback to standard behavior.', err);
  }
}

// 并发初始化
Promise.all([initBundler(), initBrowser()]).catch(console.error);

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
      timeoutInMilliseconds: 120000,
      chromiumOptions: globalBrowserExecutable ? { browserExecutable: globalBrowserExecutable } : undefined,
    });

    const outputLocation = path.join(BUNDLE_DIR, `${taskId}.mp4`);

    await renderMedia({
      composition,
      serveUrl: bundleUrl,
      codec: 'h264',
      outputLocation,
      inputProps: script,
      timeoutInMilliseconds: 120000, // 延长到 2 分钟，防止下载 Google 字体时网络超时
      chromiumOptions: globalBrowserExecutable ? { browserExecutable: globalBrowserExecutable } : undefined,
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
    throw err; // 继续向上抛出异常，触发外层 RabbitMQ 的 Nack (死信队列)
  }
}

const PORT = 3001;
// Nacos 服务地址 (假设和你的 Spring Boot 连的是同一个本地 Nacos)
const NACOS_SERVER = 'http://100.79.235.109:8848';
const SERVICE_NAME = 'remotion-service';
const IP = '100.93.68.52';

app.listen(PORT, async () => {
  console.log(`🚀 Remotion Render Service listening on port ${PORT}`);

  try {
    // 1. 向 Nacos 注册当前服务实例
    const registerUrl = `${NACOS_SERVER}/nacos/v1/ns/instance?serviceName=${SERVICE_NAME}&ip=${IP}&port=${PORT}`;
    await fetch(registerUrl, { method: 'POST' });
    console.log(`✅ Successfully registered to Nacos as [${SERVICE_NAME}]`);

    // 2. 维持心跳 (每隔 5 秒发一次，告诉 Nacos 我还活着)
    setInterval(async () => {
      const beatUrl = `${NACOS_SERVER}/nacos/v1/ns/instance/beat?serviceName=${SERVICE_NAME}`;
      const beatData = {
        cluster: 'DEFAULT',
        ip: IP,
        port: PORT,
        serviceName: SERVICE_NAME,
        weight: 1
      };

      try {
        await fetch(beatUrl, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: `beat=${encodeURIComponent(JSON.stringify(beatData))}`
        });
      } catch (err) {
        console.error('❌ Nacos heartbeat failed:', err);
      }
    }, 5000); // 5000ms 心跳间隔

  } catch (error) {
    console.error(`❌ Failed to register to Nacos:`, error);
  }

  // RabbitMQ 消费者初始化
  try {
    const rabbitHost = process.env.RABBITMQ_HOST || 'localhost';
    const rabbitPort = process.env.RABBITMQ_PORT || '5672';
    const rabbitUser = process.env.RABBITMQ_USERNAME || 'guest';
    const rabbitPass = process.env.RABBITMQ_PASSWORD || 'guest';
    
    console.log(`🐰 Connecting to RabbitMQ at amqp://${rabbitUser}:***@${rabbitHost}:${rabbitPort}...`);
    const connection = await amqp.connect(`amqp://${rabbitUser}:${rabbitPass}@${rabbitHost}:${rabbitPort}`);
    const channel = await connection.createChannel();
    
    const queue = 'remotion.render.queue';
    // 必须与 Java 端的 QueueBuilder 参数保持完全一致，否则 RabbitMQ 会报 PRECONDITION_FAILED 错误
    await channel.assertQueue(queue, { 
      durable: true,
      arguments: {
        'x-dead-letter-exchange': 'remotion.dlx.exchange',
        'x-dead-letter-routing-key': 'remotion.render.dlq.routing',
        'x-message-ttl': 60000,
        'x-max-length': 1000
      }
    });
    // 一次只处理一个渲染任务 (QoS)
    await channel.prefetch(1);
    
    console.log(`✅ RabbitMQ connected. Waiting for tasks in queue: ${queue}`);
    
    channel.consume(queue, async (msg) => {
      if (msg !== null) {
        try {
          const content = JSON.parse(msg.content.toString());
          const { taskId, compositionScript } = content;
          console.log(`📥 Received render task from RabbitMQ: ${taskId}`);

          if (!bundleLocation) {
             console.error('❌ Bundler not ready, rejecting task');
             channel.nack(msg, false, true); // requeue
             return;
          }

          const parsed = CompositionScriptSchema.safeParse(compositionScript);
          if (!parsed.success) {
            console.error(`❌ Invalid composition script for ${taskId}`);
            // 语法验证失败也是一种业务失败，拒绝并丢入死信队列退费
            channel.nack(msg, false, false);
            return;
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

          // 处理渲染
          await processRender(taskId, parsed.data, bundleLocation);
          
          // 渲染完成后手动 ACK 确认
          channel.ack(msg);
          console.log(`✅ Task ${taskId} acknowledged in RabbitMQ`);
        } catch (err) {
          console.error('❌ Failed to process RabbitMQ message, sending to DLQ:', err);
          // 核心：发生任何异常导致渲染失败时，绝不重新入队 (requeue: false)
          // 这样 RabbitMQ 会自动把它踢进配置好的 DLX 交换机
          channel.nack(msg, false, false); 
        }
      }
    });
  } catch (error) {
    console.error('❌ RabbitMQ Connection Failed:', error);
  }
});
