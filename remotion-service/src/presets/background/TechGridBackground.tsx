/**
 * 预设: bg.tech_grid — 科技网格背景
 * 适合教程、SaaS、数码、极简科技风包装
 */
import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';

interface TechGridBackgroundProps {
  backgroundColor?: string;
  lineColor?: string;
  accentColor?: string;
  gridSize?: number;
  lineOpacity?: number;
  driftSpeed?: number;
  layoutMode?: 'auto' | 'portrait' | 'landscape';
}

const gridSvg = (gridSize: number, lineColor: string, accentColor: string, lineOpacity: number): string => {
  const encoded = encodeURIComponent(`
    <svg xmlns="http://www.w3.org/2000/svg" width="${gridSize}" height="${gridSize}" viewBox="0 0 ${gridSize} ${gridSize}">
      <rect width="${gridSize}" height="${gridSize}" fill="none"/>
      <path d="M ${gridSize} 0 L 0 0 0 ${gridSize}" fill="none" stroke="${lineColor}" stroke-opacity="${lineOpacity}" stroke-width="1"/>
      <circle cx="${gridSize / 2}" cy="${gridSize / 2}" r="1.6" fill="${accentColor}" fill-opacity="0.55"/>
    </svg>
  `);
  return `url("data:image/svg+xml;utf8,${encoded}")`;
};

export const TechGridBackground: React.FC<TechGridBackgroundProps> = ({
  backgroundColor = '#06111F',
  lineColor = '#7DD3FC',
  accentColor = '#22D3EE',
  gridSize = 76,
  lineOpacity = 0.26,
  driftSpeed = 18,
  layoutMode = 'auto',
}) => {
  const frame = useCurrentFrame();
  const { width, height } = useVideoConfig();
  const isLandscape = layoutMode === 'landscape' || (layoutMode === 'auto' && width > height);
  const yOffset = interpolate(frame % 180, [0, 179], [0, driftSpeed], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const resolvedGridSize = isLandscape ? Math.max(60, gridSize - 10) : gridSize;
  const rotateX = isLandscape ? 64 : 72;
  const scale = isLandscape ? 1.35 : 1.55;
  const transformOrigin = isLandscape ? 'center 78%' : 'center 72%';

  return (
    <AbsoluteFill
      style={{
        background: `linear-gradient(180deg, ${backgroundColor} 0%, #0f1f3a 55%, #102d56 100%)`,
        overflow: 'hidden',
      }}
    >
      <AbsoluteFill
        style={{
          backgroundImage: gridSvg(resolvedGridSize, lineColor, accentColor, lineOpacity),
          backgroundRepeat: 'repeat',
          backgroundSize: `${resolvedGridSize}px ${resolvedGridSize}px`,
          transform: `translateY(${yOffset}px) perspective(${isLandscape ? 1500 : 1200}px) rotateX(${rotateX}deg) scale(${scale})`,
          transformOrigin,
          opacity: 0.92,
        }}
      />
      <AbsoluteFill
        style={{
          background:
            'linear-gradient(180deg, rgba(6,17,31,0) 0%, rgba(6,17,31,0.2) 52%, rgba(6,17,31,0.9) 100%)',
        }}
      />
    </AbsoluteFill>
  );
};
