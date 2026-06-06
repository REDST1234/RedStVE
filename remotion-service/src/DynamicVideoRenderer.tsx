/**
 * DynamicVideoRenderer — 主渲染器
 * 解析 Composition Script JSON，组装 TransitionSeries + BGM 音轨
 */
import React from 'react';
import { AbsoluteFill, Sequence, interpolate, useVideoConfig } from 'remotion';
import { Audio } from '@remotion/media';
import { TransitionSeries } from '@remotion/transitions';
import { SceneRenderer } from './SceneRenderer';
import { resolvePresentation, resolveTiming } from './presets/transition/resolvers';
import type { CompositionScript, Scene } from './schemas/CompositionScript';

/** 尾音效最多延长的帧数（3秒@30fps），防止异常数据拖太久 */
const MAX_TRAILING_FRAMES = 90;

/**
 * 计算最后一场景中音效层的溢出帧数。
 * 只考虑 media.audio 层：如果它的 (from + durationInFrames) 超过了场景边界，
 * 就累加到溢出量里，让视频延长以播完尾音效。
 */
const computeAudioOverflow = (scene: Scene): number => {
  let maxEnd = scene.durationInFrames;
  for (const layer of scene.layers) {
    if (layer.preset !== 'media.audio') continue;
    const params = layer.params as Record<string, unknown> | undefined;
    // 自身时长优先用 layer.durationInFrames，其次 totalDurationFrames
    const ownDur = layer.durationInFrames
      ?? (typeof params?.totalDurationFrames === 'number' ? Math.round(params.totalDurationFrames as number) : 0)
      ?? scene.durationInFrames;
    const from = layer.enterAtFrame ?? 0;

    // 如果有 syncWithLayerId → trigger_on_end，起点在目标层末尾
    let effectiveFrom = from;
    if (params?.syncWithLayerId) {
      const syncMode = (params.syncMode as string) ?? 'match_layer';
      const targetLayer = scene.layers.find((c) => c.layerId === params.syncWithLayerId);
      if (targetLayer) {
        const targetDur = targetLayer.durationInFrames ?? scene.durationInFrames;
        const targetFrom = targetLayer.enterAtFrame ?? 0;
        if (syncMode === 'trigger_on_end' || syncMode === 'trigger_on_typing_end') {
          effectiveFrom = targetFrom + targetDur;
        } else {
          effectiveFrom = targetFrom;
        }
      }
    }

    const end = effectiveFrom + ownDur;
    if (end > maxEnd) maxEnd = end;
  }

  return Math.max(0, Math.min(MAX_TRAILING_FRAMES, maxEnd - scene.durationInFrames));
};

export const DynamicVideoRenderer: React.FC<CompositionScript> = (script) => {
  const bgColor = script.globalStyle?.backgroundColor ?? '#000000';

  // 计算最后一场景的尾音效溢出
  const lastScene = script.scenes[script.scenes.length - 1];
  const audioOverflow = computeAudioOverflow(lastScene);

  return (
    <AbsoluteFill style={{ backgroundColor: bgColor }}>
      {/* 全局 BGM 音轨 */}
      {script.bgm && (
        <Sequence from={0} layout="none">
          <BgmLayer bgm={script.bgm} />
        </Sequence>
      )}

      {/* 场景序列 + 转场 */}
      <TransitionSeries>
        {script.scenes.map((scene, i) => {
          const trans = script.transitions.find((t) => t.fromSceneIndex === i);
          const isLast = i === script.scenes.length - 1;
          const effectiveDuration = isLast ? scene.durationInFrames + audioOverflow : scene.durationInFrames;

          return (
            <React.Fragment key={scene.sceneId}>
              <TransitionSeries.Sequence durationInFrames={effectiveDuration}>
                <SceneRenderer
                  scene={scene}
                  sceneEndExtension={isLast ? audioOverflow : 0}
                />
              </TransitionSeries.Sequence>
              {trans && (
                <TransitionSeries.Transition
                  presentation={resolvePresentation(trans)}
                  timing={resolveTiming(trans)}
                />
              )}
            </React.Fragment>
          );
        })}
      </TransitionSeries>
    </AbsoluteFill>
  );
};

// ========== BGM 内部组件 ==========

interface BgmLayerProps {
  bgm: NonNullable<CompositionScript['bgm']>;
}

const BgmLayer: React.FC<BgmLayerProps> = ({ bgm }) => {
  const { durationInFrames: totalDuration } = useVideoConfig();
  const fadeIn = bgm.fadeInFrames ?? 0;
  const fadeOut = bgm.fadeOutFrames ?? 0;
  const baseVolume = bgm.volume ?? 0.3;

  const volumeFn = (frame: number): number => {
    let v = baseVolume;
    // 渐入
    if (fadeIn > 0 && frame < fadeIn) {
      v *= interpolate(frame, [0, fadeIn], [0, 1], {
        extrapolateLeft: 'clamp',
        extrapolateRight: 'clamp',
      });
    }
    // 渐出 — 视频结束时平滑收尾
    if (fadeOut > 0 && frame > totalDuration - fadeOut) {
      v *= interpolate(frame, [totalDuration - fadeOut, totalDuration], [1, 0], {
        extrapolateLeft: 'clamp',
        extrapolateRight: 'clamp',
      });
    }
    return v;
  };

  return (
    <Audio
      src={bgm.src}
      volume={volumeFn}
      loop={bgm.loop ?? true}
    />
  );
};
