/**
 * SceneRenderer — 场景渲染器
 * 遍历场景内的 layers，根据 preset ID 从注册表取出组件并传入 params
 */
import React from 'react';
import { AbsoluteFill, Sequence } from 'remotion';
import { getPreset } from './presets/PresetRegistry';
import { registerAllPresets } from './presets/registerAll';
import type { Scene } from './schemas/CompositionScript';

interface SceneRendererProps {
  scene: Scene;
  /** 允许音效层超出场景视觉边界的额外帧数（用于尾音效不截断） */
  sceneEndExtension?: number;
}

type AudioSyncMode =
  | 'match_layer'
  | 'match_typing'
  | 'trigger_on_start'
  | 'trigger_on_end'
  | 'trigger_on_typing_end';

type AudioLayerParams = Record<string, unknown> & {
  totalDurationFrames?: number;
  syncWithLayerId?: string;
  syncMode?: AudioSyncMode;
  offsetFrames?: number;
};

const AUDIO_PROTOCOL_FIELDS = new Set([
  'audioRole',
  'cueType',
  'syncWithLayerId',
  'syncMode',
  'offsetFrames',
]);

const toSafePositiveInt = (value: unknown): number | null => {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return null;
  }
  const rounded = Math.round(value);
  return rounded > 0 ? rounded : null;
};

const toSafeInt = (value: unknown, fallback = 0): number => {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return fallback;
  }
  return Math.round(value);
};

const deriveTypewriterDuration = (targetLayer: Scene['layers'][number], fallbackDuration: number): number => {
  if (targetLayer.preset !== 'text.typewriter') {
    return fallbackDuration;
  }
  const params = (targetLayer.params ?? {}) as Record<string, unknown>;
  const text = typeof params.text === 'string' ? params.text : '';
  const charCount = Math.max(1, Array.from(text).length);
  const charIntervalFrames = toSafePositiveInt(params.charIntervalFrames) ?? 2;
  return Math.min(fallbackDuration, Math.max(1, charCount * charIntervalFrames));
};

const stripAudioProtocolParams = (params: Record<string, unknown>): Record<string, unknown> =>
  Object.fromEntries(Object.entries(params).filter(([key]) => !AUDIO_PROTOCOL_FIELDS.has(key)));

const resolveAudioLayerTiming = (
  scene: Scene,
  layer: Scene['layers'][number],
  sceneEndExtension: number = 0,
): { from: number; durationInFrames: number; params: Record<string, unknown> } => {
  const params = ((layer.params ?? {}) as AudioLayerParams);
  const sceneDuration = scene.durationInFrames;
  const effectiveSceneEnd = sceneDuration + sceneEndExtension;
  const ownDuration =
    layer.durationInFrames
    ?? toSafePositiveInt(params.totalDurationFrames)
    ?? sceneDuration;
  const defaultFrom = Math.max(0, layer.enterAtFrame ?? 0);

  if (layer.preset !== 'media.audio' || !params.syncWithLayerId) {
    // 音效层允许超出场景视觉边界，视觉层仍受 sceneDuration 限制
    const maxDur = Math.max(sceneDuration, effectiveSceneEnd);
    const durationInFrames = Math.max(1, Math.min(maxDur, ownDuration));
    return {
      from: defaultFrom,
      durationInFrames,
      params: {
        ...stripAudioProtocolParams(params),
        totalDurationFrames: durationInFrames,
      },
    };
  }

  const targetLayer = scene.layers.find((candidate) => candidate.layerId === params.syncWithLayerId);
  if (!targetLayer) {
    const maxDur = Math.max(sceneDuration, effectiveSceneEnd);
    const durationInFrames = Math.max(1, Math.min(maxDur, ownDuration));
    return {
      from: defaultFrom,
      durationInFrames,
      params: {
        ...stripAudioProtocolParams(params),
        totalDurationFrames: durationInFrames,
      },
    };
  }

  const targetFrom = Math.max(0, targetLayer.enterAtFrame ?? 0);
  const targetDuration = Math.max(1, targetLayer.durationInFrames ?? sceneDuration);
  const typingDuration = deriveTypewriterDuration(targetLayer, targetDuration);
  const syncMode = params.syncMode ?? 'match_layer';
  const offsetFrames = toSafeInt(params.offsetFrames, 0);

  let from = defaultFrom;
  let durationInFrames = ownDuration;

  switch (syncMode) {
    case 'match_typing':
      from = targetFrom + offsetFrames;
      durationInFrames = typingDuration;
      break;
    case 'trigger_on_start':
      from = targetFrom + offsetFrames;
      durationInFrames = ownDuration;
      break;
    case 'trigger_on_end':
      from = targetFrom + targetDuration + offsetFrames;
      durationInFrames = ownDuration;
      break;
    case 'trigger_on_typing_end':
      from = targetFrom + typingDuration + offsetFrames;
      durationInFrames = ownDuration;
      break;
    case 'match_layer':
    default:
      from = targetFrom + offsetFrames;
      durationInFrames = targetDuration;
      break;
  }

  const clampedFrom = Math.max(0, Math.min(sceneDuration - 1, from));
  const maxDuration = Math.max(1, effectiveSceneEnd - clampedFrom);

  const finalDuration = Math.max(1, Math.min(maxDuration, durationInFrames));
  return {
    from: clampedFrom,
    durationInFrames: finalDuration,
    params: {
      ...stripAudioProtocolParams(params),
      totalDurationFrames: finalDuration,
    },
  };
};

export const SceneRenderer: React.FC<SceneRendererProps> = ({ scene, sceneEndExtension = 0 }) => {
  // 防止服务端渲染链路遗漏入口副作用，渲染前兜底注册全部预设。
  registerAllPresets();

  return (
    <AbsoluteFill>
      {scene.layers.map((layer) => {
        const { component: PresetComponent } = getPreset(layer.preset);
        const resolvedAudio = resolveAudioLayerTiming(scene, layer, sceneEndExtension);
        const sequenceFrom = layer.preset === 'media.audio'
          ? resolvedAudio.from
          : (layer.enterAtFrame ?? 0);
        const sequenceDuration = layer.preset === 'media.audio'
          ? resolvedAudio.durationInFrames
          : (layer.durationInFrames ?? scene.durationInFrames);
        const renderParams = layer.preset === 'media.audio'
          ? resolvedAudio.params
          : layer.params;

        return (
          <Sequence
            key={layer.layerId}
            from={sequenceFrom}
            durationInFrames={sequenceDuration}
            layout="none"
          >
            <PresetComponent {...renderParams} />
          </Sequence>
        );
      })}
    </AbsoluteFill>
  );
};
