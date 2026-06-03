/**
 * 预设: backing.solid_plate — 实色背景底板
 * 为文字/元素提供半透明背景垫层，提高可读性
 */
import React from 'react';
import { interpolate, useCurrentFrame } from 'remotion';
import { resolveBackingPosition } from './normalizeBackingPosition';

interface SolidPlateProps {
  width?: string | number;
  height?: string | number;
  color?: string;
  opacity?: number;
  borderRadius?: number;
  padding?: number;
  position?: { x?: string | number; y?: string | number };
  borderColor?: string;
  borderWidth?: number;
  shadowEnabled?: boolean;
  shadowColor?: string;
  enterFrames?: number;
}

const withAlpha = (color: string, opacity: number): string => {
  const clamped = Math.max(0, Math.min(1, opacity));
  const normalized = color.trim();
  if (/^#([0-9a-fA-F]{6})$/.test(normalized)) {
    return `${normalized}${Math.round(clamped * 255)
      .toString(16)
      .padStart(2, '0')}`;
  }
  if (/^#([0-9a-fA-F]{3})$/.test(normalized)) {
    const expanded = normalized
      .slice(1)
      .split('')
      .map((ch) => ch + ch)
      .join('');
    return `#${expanded}${Math.round(clamped * 255)
      .toString(16)
      .padStart(2, '0')}`;
  }
  return normalized;
};

export const SolidPlate: React.FC<SolidPlateProps> = ({
  width = 'auto',
  height = 'auto',
  color = '#0B1120',
  opacity = 0.72,
  borderRadius = 20,
  padding = 32,
  position,
  borderColor,
  borderWidth = 0,
  shadowEnabled = true,
  shadowColor = 'rgba(0,0,0,0.22)',
  enterFrames = 12,
}) => {
  const frame = useCurrentFrame();
  const resolvedPosition = resolveBackingPosition(position);

  const fadeInOpacity = interpolate(frame, [0, enterFrames], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const scale = interpolate(frame, [0, enterFrames], [0.96, 1], {
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
        backgroundColor: withAlpha(color, opacity),
        borderRadius,
        border: borderWidth > 0 ? `${borderWidth}px solid ${borderColor ?? 'transparent'}` : undefined,
        boxShadow: shadowEnabled
          ? `0 24px 64px ${shadowColor}, 0 8px 24px ${shadowColor}`
          : undefined,
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
      }}
    />
  );
};
