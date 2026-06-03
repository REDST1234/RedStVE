/**
 * SceneRenderer — 场景渲染器
 * 遍历场景内的 layers，根据 preset ID 从注册表取出组件并传入 params
 */
import React from 'react';
import { AbsoluteFill, Sequence } from 'remotion';
import { getPreset } from './presets/PresetRegistry';
import type { Scene } from './schemas/CompositionScript';

interface SceneRendererProps {
  scene: Scene;
}

export const SceneRenderer: React.FC<SceneRendererProps> = ({ scene }) => {
  return (
    <AbsoluteFill>
      {scene.layers.map((layer) => {
        const { component: PresetComponent } = getPreset(layer.preset);

        return (
          <Sequence
            key={layer.layerId}
            from={layer.enterAtFrame ?? 0}
            durationInFrames={layer.durationInFrames ?? scene.durationInFrames}
            layout="none"
          >
            <PresetComponent {...layer.params} />
          </Sequence>
        );
      })}
    </AbsoluteFill>
  );
};
