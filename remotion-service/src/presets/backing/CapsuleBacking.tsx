/**
 * 预设: backing.capsule — 胶囊形背景底板
 * solid_plate 的圆角变体，默认 borderRadius=999，适合衬在短文本后面
 */
import React from 'react';
import { Easing, interpolate, useCurrentFrame } from 'remotion';
import { resolveBackingPosition } from './normalizeBackingPosition';

interface CapsuleBackingProps {
  width?: string | number;
  height?: string | number;
  color?: string;
  opacity?: number;
  borderRadius?: number;
  paddingX?: number;
  paddingY?: number;
  position?: { x?: string | number; y?: string | number };
  borderColor?: string;
  borderWidth?: number;
  shadowEnabled?: boolean;
  shadowColor?: string;
  enterFrames?: number;
  scaleFrom?: number;
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

export const CapsuleBacking: React.FC<CapsuleBackingProps> = ({
  width = 'auto',
  height = 'auto',
  color = '#3B82F6',
  opacity = 0.82,
  borderRadius = 999,
  paddingX = 28,
  paddingY = 14,
  position,
  borderColor,
  borderWidth = 0,
  shadowEnabled = true,
  shadowColor = 'rgba(0,0,0,0.18)',
  enterFrames = 12,
  scaleFrom = 0.92,
}) => {
  const frame = useCurrentFrame();
  const resolvedPosition = resolveBackingPosition(position);

  const effectiveEnter = Math.max(1, enterFrames);
  const progress = interpolate(frame, [0, effectiveEnter], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.out(Easing.back(1.2)),
  });
  const fadeInOpacity = interpolate(frame, [0, Math.max(4, effectiveEnter * 0.4)], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const scale = interpolate(progress, [0, 1], [scaleFrom, 1], {
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
        paddingLeft: isAutoWidth ? paddingX : undefined,
        paddingRight: isAutoWidth ? paddingX : undefined,
        paddingTop: isAutoHeight ? paddingY : undefined,
        paddingBottom: isAutoHeight ? paddingY : undefined,
        backgroundColor: withAlpha(color, opacity),
        borderRadius,
        border: borderWidth > 0 ? `${borderWidth}px solid ${borderColor ?? 'transparent'}` : undefined,
        boxShadow: shadowEnabled
          ? `0 18px 48px ${shadowColor}, 0 4px 14px ${shadowColor}`
          : undefined,
        opacity: fadeInOpacity,
        transform:
          resolvedPosition.style.transform
            ? `${resolvedPosition.style.transform} scale(${scale})`
            : `scale(${scale})`,
        transformOrigin: 'center center',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        boxSizing: 'border-box',
        whiteSpace: 'nowrap',
      }}
    />
  );
};
