/**
 * 预设: bg.noise_grain — 极简噪点/磨砂质感背景
 * 适合为纯色或渐变背景补一层高级纹理
 */
import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';

interface NoiseGrainBackgroundProps {
  backgroundColor?: string;
  grainOpacity?: number;
  scale?: number;
  layoutMode?: 'auto' | 'portrait' | 'landscape';
}

const noiseSvg = encodeURIComponent(`
  <svg xmlns="http://www.w3.org/2000/svg" width="180" height="180" viewBox="0 0 180 180">
    <filter id="noise">
      <feTurbulence type="fractalNoise" baseFrequency="0.95" numOctaves="3" stitchTiles="stitch" />
      <feColorMatrix type="saturate" values="0"/>
      <feComponentTransfer>
        <feFuncA type="table" tableValues="0 0.05 0.08 0.02 0.06 0.03"/>
      </feComponentTransfer>
    </filter>
    <rect width="180" height="180" filter="url(#noise)" opacity="0.85"/>
  </svg>
`);

export const NoiseGrainBackground: React.FC<NoiseGrainBackgroundProps> = ({
  backgroundColor = '#101827',
  grainOpacity = 0.16,
  scale = 1,
  layoutMode = 'auto',
}) => {
  const frame = useCurrentFrame();
  const { width, height } = useVideoConfig();
  const isLandscape = layoutMode === 'landscape' || (layoutMode === 'auto' && width > height);
  const driftX = interpolate(frame % 90, [0, 89], [0, isLandscape ? 8 : 6], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const driftY = interpolate(frame % 120, [0, 119], [0, isLandscape ? 4 : 5], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const resolvedScale = isLandscape ? scale * 0.92 : scale;

  return (
    <AbsoluteFill
      style={{
        backgroundColor,
        overflow: 'hidden',
      }}
    >
      <AbsoluteFill
        style={{
          opacity: grainOpacity,
          mixBlendMode: 'soft-light',
          backgroundImage: `url("data:image/svg+xml;utf8,${noiseSvg}")`,
          backgroundRepeat: 'repeat',
          backgroundSize: `${180 * resolvedScale}px ${180 * resolvedScale}px`,
          transform: `translate(${driftX}px, ${driftY}px)`,
        }}
      />
    </AbsoluteFill>
  );
};
