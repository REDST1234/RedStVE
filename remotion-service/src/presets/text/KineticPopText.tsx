/**
 * 预设: text.kinetic_pop — 冲击型弹簧大字报
 * 适合 hook、数字卖点、短句强调和 CTA 爆点
 */
import React from 'react';
import { AbsoluteFill, Easing, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';
import { getFontFamily, FontTier } from '../../fontSystem';
import { LayoutMode, resolveTextLayout, TextPositionPreset } from './layout';

interface KineticPopTextProps {
  text: string;
  fontSize?: number;
  color?: string;
  fontWeight?: number;
  position?: { x: string | number; y: string | number };
  positionPreset?: TextPositionPreset;
  layoutMode?: LayoutMode;
  scaleFrom?: number;
  scaleTo?: number;
  rotationFrom?: number;
  rotationTo?: number;
  enterFrames?: number;
  settleFrames?: number;
  textShadow?: string;
  letterSpacing?: string | number;
  textAlign?: 'left' | 'center' | 'right';
  maxWidth?: string | number;
  /** 字体分层，默认 'accent'（Ma Shan Zheng 书法体） */
  fontTier?: FontTier;
}

export const KineticPopText: React.FC<KineticPopTextProps> = ({
  text,
  fontSize = 112,
  color = '#FFFFFF',
  fontWeight = 900,
  position,
  positionPreset,
  layoutMode = 'auto',
  scaleFrom = 1.8,
  scaleTo = 1,
  rotationFrom = -7,
  rotationTo = 0,
  enterFrames = 14,
  settleFrames = 24,
  textShadow = '0 10px 34px rgba(0,0,0,0.35)',
  letterSpacing = 0,
  textAlign,
  maxWidth = '86%',
  fontTier = 'accent',
}) => {
  const fontFamily = getFontFamily(fontTier);
  const frame = useCurrentFrame();
  const { durationInFrames, width, height } = useVideoConfig();
  const layout = resolveTextLayout({
    width,
    height,
    layoutMode,
    position,
    positionPreset,
    portraitDefaultY: '38%',
    landscapeDefaultY: '46%',
    portraitMaxWidth: maxWidth,
    landscapeMaxWidth: typeof maxWidth === 'undefined' ? '72%' : maxWidth,
  });
  const safeDuration = Math.max(3, durationInFrames);
  const totalEnter = Math.max(6, enterFrames);
  const totalSettle = Math.max(totalEnter + 6, settleFrames);
  const fadeInEnd = Math.min(4, safeDuration - 2);
  const desiredExitStart = Math.min(safeDuration - 1, totalSettle + 12);
  const exitStart = Math.max(fadeInEnd + 1, desiredExitStart);

  const popProgress = interpolate(frame, [0, totalEnter], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.out(Easing.back(1.65)),
  });
  const settleProgress = interpolate(frame, [totalEnter, totalSettle], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.out(Easing.quad),
  });

  const scale = interpolate(popProgress, [0, 1], [scaleFrom, scaleTo], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const rotation = interpolate(popProgress, [0, 1], [rotationFrom, rotationTo], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const yOffset = interpolate(settleProgress, [0, 1], [28, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const opacity = interpolate(frame, [0, fadeInEnd, exitStart, safeDuration], [0, 1, 1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  return (
    <AbsoluteFill>
      <div
        style={{
          ...layout.wrapperStyle,
          maxWidth: layout.maxWidth,
          fontFamily,
          fontSize,
          fontWeight,
          color,
          lineHeight: 1.1,
          letterSpacing,
          textAlign: textAlign ?? layout.textAlign,
          textShadow,
          opacity,
          transform: `${layout.wrapperStyle.transform ?? ''} translateY(${yOffset}px) rotate(${rotation}deg) scale(${scale})`.trim(),
          transformOrigin: 'center center',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}
      >
        {text}
      </div>
    </AbsoluteFill>
  );
};
