/**
 * 预设: bg.mesh_gradient — 流体渐变背景
 * 适合 MG、口播包装、无素材快闪背景
 */
import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';

interface MeshGradientBackgroundProps {
  colors?: string[];
  intensity?: number;
  blendMode?: React.CSSProperties['mixBlendMode'];
  layoutMode?: 'auto' | 'portrait' | 'landscape';
}

const fallbackColors = ['#0F2027', '#203A43', '#2C5364', '#7C3AED'];

export const MeshGradientBackground: React.FC<MeshGradientBackgroundProps> = ({
  colors = fallbackColors,
  intensity = 0.92,
  blendMode = 'screen',
  layoutMode = 'auto',
}) => {
  const frame = useCurrentFrame();
  const { durationInFrames, width, height } = useVideoConfig();
  const safeColors = colors.length >= 4 ? colors : [...colors, ...fallbackColors].slice(0, 4);
  const isLandscape = layoutMode === 'landscape' || (layoutMode === 'auto' && width > height);

  const drift = (offset: number, amplitude: number, cycle = durationInFrames) =>
    interpolate((frame + offset) % Math.max(1, cycle), [0, cycle / 2, cycle], [-amplitude, amplitude, -amplitude], {
      extrapolateLeft: 'clamp',
      extrapolateRight: 'clamp',
    });

  const spotA = {
    x: (isLandscape ? 14 : 18) + drift(0, isLandscape ? 10 : 8),
    y: (isLandscape ? 28 : 24) + drift(18, isLandscape ? 6 : 7),
  };
  const spotB = {
    x: (isLandscape ? 52 : 72) + drift(36, isLandscape ? 12 : 10),
    y: (isLandscape ? 18 : 20) + drift(12, isLandscape ? 7 : 8),
  };
  const spotC = {
    x: (isLandscape ? 26 : 32) + drift(54, isLandscape ? 11 : 9),
    y: (isLandscape ? 70 : 74) + drift(24, isLandscape ? 7 : 10),
  };
  const spotD = {
    x: (isLandscape ? 84 : 78) + drift(10, isLandscape ? 8 : 7),
    y: (isLandscape ? 62 : 70) + drift(46, isLandscape ? 6 : 8),
  };
  const spotSizeA = isLandscape ? 30 : 38;
  const spotSizeB = isLandscape ? 28 : 36;
  const spotSizeC = isLandscape ? 26 : 34;
  const spotSizeD = isLandscape ? 24 : 28;
  const blurAmount = isLandscape ? 8 : 6;
  const scaleAmount = isLandscape ? 1.03 : 1.04;

  return (
    <AbsoluteFill
      style={{
        backgroundColor: safeColors[0],
        overflow: 'hidden',
      }}
    >
      <AbsoluteFill
        style={{
          background: [
            `radial-gradient(circle at ${spotA.x}% ${spotA.y}%, ${safeColors[1]} 0%, transparent ${spotSizeA}%)`,
            `radial-gradient(circle at ${spotB.x}% ${spotB.y}%, ${safeColors[2]} 0%, transparent ${spotSizeB}%)`,
            `radial-gradient(circle at ${spotC.x}% ${spotC.y}%, ${safeColors[3]} 0%, transparent ${spotSizeC}%)`,
            `radial-gradient(circle at ${spotD.x}% ${spotD.y}%, rgba(255,255,255,0.22) 0%, transparent ${spotSizeD}%)`,
          ].join(','),
          filter: `blur(${blurAmount}px)`,
          opacity: intensity,
          mixBlendMode: blendMode,
          transform: `scale(${scaleAmount + Math.sin(frame / 36) * 0.015})`,
        }}
      />
    </AbsoluteFill>
  );
};
