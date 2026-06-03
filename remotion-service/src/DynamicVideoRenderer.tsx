/**
 * DynamicVideoRenderer — 主渲染器
 * 解析 Composition Script JSON，组装 TransitionSeries + BGM 音轨
 */
import React from 'react';
import { AbsoluteFill, Sequence, interpolate } from 'remotion';
import { Audio } from '@remotion/media';
import { TransitionSeries } from '@remotion/transitions';
import { SceneRenderer } from './SceneRenderer';
import { resolvePresentation, resolveTiming } from './presets/transition/resolvers';
import type { CompositionScript } from './schemas/CompositionScript';



export const DynamicVideoRenderer: React.FC<CompositionScript> = (script) => {
  const bgColor = script.globalStyle?.backgroundColor ?? '#000000';

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
          return (
            <React.Fragment key={scene.sceneId}>
              <TransitionSeries.Sequence durationInFrames={scene.durationInFrames}>
                <SceneRenderer scene={scene} />
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
  const fadeIn = bgm.fadeInFrames ?? 0;
  const baseVolume = bgm.volume ?? 0.3;

  const volumeFn = (frame: number): number => {
    let v = baseVolume;
    if (fadeIn > 0 && frame < fadeIn) {
      v *= interpolate(frame, [0, fadeIn], [0, 1], {
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
