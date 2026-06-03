/**
 * 预设: backing.glass_plate — 玻璃磨砂底板
 * iOS-style 毛玻璃效果。SSR 不支持 backdrop-filter，
 * 通过渐变叠加 + SVG noise 纹理降级模拟。
 */
import React, { useId } from 'react';
import { interpolate, useCurrentFrame } from 'remotion';
import { resolveBackingPosition } from './normalizeBackingPosition';

interface GlassPlateProps {
  width?: string | number;
  height?: string | number;
  blurAmount?: number;
  tintColor?: string;
  borderRadius?: number;
  borderColor?: string;
  borderWidth?: number;
  padding?: number;
  position?: { x?: string | number; y?: string | number };
  shadowEnabled?: boolean;
  shadowColor?: string;
  enterFrames?: number;
}

/**
 * 内联 SVG noise 纹理——复用 bg.noise_grain 的生成策略
 * blurAmount 控制颗粒尺寸：值越大颗粒越细
 */
const GlassNoiseTexture: React.FC<{ blurAmount: number; seed?: number }> = ({
  blurAmount,
  seed = 42,
}) => {
  const noiseId = useId();
  const baseFreq = Math.max(0.6, Math.min(2.8, 20 / Math.max(8, blurAmount)));
  const numOctaves = Math.max(2, Math.min(5, Math.round(blurAmount / 6)));

  // 基于 seed 偏移其中一个滤镜，产生不同的 noise 形态
  const freqX = (baseFreq * (0.92 + (seed % 10) * 0.016)).toFixed(3);
  const freqY = (baseFreq * (0.88 + ((seed * 7) % 10) * 0.018)).toFixed(3);

  return (
    <svg
      style={{
        position: 'absolute',
        inset: 0,
        width: '100%',
        height: '100%',
        opacity: 0.10,
        pointerEvents: 'none',
      }}
      aria-hidden="true"
    >
      <filter id={`glass-noise-${noiseId}`}>
        <feTurbulence
          type="fractalNoise"
          baseFrequency={`${freqX} ${freqY}`}
          numOctaves={numOctaves}
          seed={seed}
          result="noise"
        />
        <feColorMatrix
          type="saturate"
          values="0"
          in="noise"
          result="grayNoise"
        />
      </filter>
      <rect
        width="100%"
        height="100%"
        filter={`url(#glass-noise-${noiseId})`}
        opacity="0.7"
      />
    </svg>
  );
};

export const GlassPlate: React.FC<GlassPlateProps> = ({
  width = 'auto',
  height = 'auto',
  blurAmount = 20,
  tintColor = 'rgba(255,255,255,0.12)',
  borderRadius = 24,
  borderColor = 'rgba(255,255,255,0.18)',
  borderWidth = 1,
  padding = 36,
  position,
  shadowEnabled = true,
  shadowColor = 'rgba(0,0,0,0.18)',
  enterFrames = 14,
}) => {
  const frame = useCurrentFrame();

  const resolvedPosition = resolveBackingPosition(position);

  const fadeInOpacity = interpolate(frame, [0, enterFrames], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const scale = interpolate(frame, [0, enterFrames], [0.97, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  const isAutoWidth = width === 'auto';
  const isAutoHeight = height === 'auto';

  return (
    <div
      style={{
        ...resolvedPosition.style,
        width: isAutoWidth ? undefined : width,
        height: isAutoHeight ? undefined : height,
        padding: isAutoWidth || isAutoHeight ? padding : undefined,
        background: `linear-gradient(135deg, ${tintColor}, rgba(255,255,255,0.02))`,
        borderRadius,
        border: borderWidth > 0 ? `${borderWidth}px solid ${borderColor}` : undefined,
        boxShadow: shadowEnabled
          ? `0 24px 64px ${shadowColor}, inset 0 1px 0 rgba(255,255,255,0.08)`
          : `inset 0 1px 0 rgba(255,255,255,0.08)`,
        opacity: fadeInOpacity,
        transform:
          resolvedPosition.style.transform
            ? `${resolvedPosition.style.transform} scale(${scale})`
            : `scale(${scale})`,
        transformOrigin: 'center center',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        boxSizing: 'border-box',
        overflow: 'hidden',
      }}
    >
      {/* SVG noise 纹理层 */}
      <GlassNoiseTexture blurAmount={blurAmount} seed={42} />
      {/* 互补方向的微妙渐变，增强玻璃层次感 */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          background: `radial-gradient(circle at 40% 30%, rgba(255,255,255,0.08) 0%, transparent 55%)`,
          pointerEvents: 'none',
          borderRadius,
        }}
      />
    </div>
  );
};
