/**
 * 预设: overlay.light_leak — 光效叠加封装
 */
import React from 'react';
import { LightLeak } from '@remotion/light-leaks';

interface LightLeakWrapperProps {
  seed?: number;
  hueShift?: number;
  durationInFrames?: number;
}

export const LightLeakWrapper: React.FC<LightLeakWrapperProps> = ({
  seed = 0,
  hueShift = 0,
  durationInFrames,
}) => {
  return (
    <LightLeak
      seed={seed}
      hueShift={hueShift}
      durationInFrames={durationInFrames}
    />
  );
};
