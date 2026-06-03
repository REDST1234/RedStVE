import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { bundle } from '@remotion/bundler';
import { renderMedia, selectComposition } from '@remotion/renderer';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const frontendRoot = path.resolve(__dirname, '..');
const repoRoot = path.resolve(frontendRoot, '..');

const defaultProps = {
  title: 'Remotion Render Smoke Test',
  subtitle: '后续这里会替换为后端生成的创作时间线 props。',
  backgroundColor: '#020617',
  segments: [
    {
      id: 'seg-1',
      label: 'Remotion Ready',
      startFrame: 0,
      durationInFrames: 90,
      color: '#2563EB',
      description: '如果你能看到这一段，说明 bundle 与 renderMedia 已经打通。',
    },
    {
      id: 'seg-2',
      label: 'Timeline Bridge',
      startFrame: 90,
      durationInFrames: 120,
      color: '#0F766E',
      description: '下一步将接后端 Composition Timeline JSON 到 Remotion props。',
    },
    {
      id: 'seg-3',
      label: 'Render Output',
      startFrame: 210,
      durationInFrames: 90,
      color: '#EA580C',
      description: '当前脚本会默认输出到 storage/remotion/renders/demo.mp4。',
    },
  ],
};

const parseArgs = (argv) => {
  const parsed = {};
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (!token.startsWith('--')) {
      continue;
    }
    const key = token.slice(2);
    const next = argv[index + 1];
    if (!next || next.startsWith('--')) {
      parsed[key] = 'true';
      continue;
    }
    parsed[key] = next;
    index += 1;
  }
  return parsed;
};

const readJsonIfExists = async (filePath) => {
  try {
    const content = await fs.readFile(filePath, 'utf8');
    return JSON.parse(content);
  } catch (error) {
    if (error && typeof error === 'object' && 'code' in error && error.code === 'ENOENT') {
      return null;
    }
    throw error;
  }
};

const ensureDir = async (directory) => {
  await fs.mkdir(directory, { recursive: true });
};

const resolveOutputLocation = (cliArgs) => {
  const configuredOutput = cliArgs.output ?? process.env.REMOTION_RENDER_OUT;
  if (!configuredOutput) {
    return path.resolve(repoRoot, 'storage', 'remotion', 'renders', 'demo.mp4');
  }
  if (configuredOutput.toLowerCase().endsWith('.mp4')) {
    return path.resolve(frontendRoot, configuredOutput);
  }
  return path.resolve(frontendRoot, configuredOutput, 'demo.mp4');
};

const resolveEntry = () => {
  const configuredEntry = process.env.REMOTION_ENTRY;
  return configuredEntry
    ? path.resolve(frontendRoot, configuredEntry)
    : path.resolve(frontendRoot, 'remotion', 'index.ts');
};

const resolveInputProps = async (cliArgs) => {
  if (cliArgs['props-file']) {
    const absolutePath = path.resolve(frontendRoot, cliArgs['props-file']);
    const fromFile = await readJsonIfExists(absolutePath);
    if (!fromFile) {
      throw new Error(`props file not found: ${absolutePath}`);
    }
    return fromFile;
  }
  if (cliArgs.props) {
    return JSON.parse(cliArgs.props);
  }
  return defaultProps;
};

const main = async () => {
  const cliArgs = parseArgs(process.argv.slice(2));
  const entryPoint = resolveEntry();
  const outputLocation = resolveOutputLocation(cliArgs);
  const compositionId = cliArgs.composition ?? 'TimelineComposition';
  const inputProps = await resolveInputProps(cliArgs);

  await ensureDir(path.dirname(outputLocation));

  const browserExecutable = process.env.REMOTION_BROWSER_EXECUTABLE;

  console.info('[remotion] entryPoint=', entryPoint);
  console.info('[remotion] compositionId=', compositionId);
  console.info('[remotion] outputLocation=', outputLocation);

  const bundled = await bundle({
    entryPoint,
    onProgress: ({ progress }) => {
      const percent = Math.round(progress * 100);
      console.info(`[remotion] bundling ${percent}%`);
    },
  });

  const composition = await selectComposition({
    serveUrl: bundled,
    id: compositionId,
    inputProps,
  });

  await renderMedia({
    serveUrl: bundled,
    composition,
    codec: 'h264',
    outputLocation,
    inputProps,
    browserExecutable: browserExecutable || undefined,
    onProgress: ({ renderedFrames, encodedFrames, progress }) => {
      const percent = Math.round(progress * 100);
      console.info(
        `[remotion] render ${percent}% renderedFrames=${renderedFrames} encodedFrames=${encodedFrames}`,
      );
    },
  });

  console.info(`[remotion] render completed: ${outputLocation}`);
};

main().catch((error) => {
  console.error('[remotion] render failed');
  console.error(error);
  process.exitCode = 1;
});
