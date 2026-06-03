/**
 * 预设: text.mask_reveal — 遮罩滑入文字
 * 适合功能说明、科技感标题、克制型中段文案
 */
import React from 'react';
import { AbsoluteFill, Easing, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';
import { loadFont } from '@remotion/google-fonts/NotoSansSC';
import { LayoutMode, resolveTextLayout, TextPositionPreset } from './layout';

const { fontFamily } = loadFont();

interface MaskRevealTextProps {
  text: string;
  fontSize?: number;
  color?: string;
  fontWeight?: number;
  position?: { x: string | number; y: string | number };
  positionPreset?: TextPositionPreset;
  layoutMode?: LayoutMode;
  revealDirection?: 'left_to_right' | 'right_to_left' | 'bottom_to_top' | 'top_to_bottom';
  revealFrames?: number;
  textShadow?: string;
  letterSpacing?: string | number;
  textAlign?: 'left' | 'center' | 'right';
  maxWidth?: string | number;
  maskPadding?: number;
}

export const MaskRevealText: React.FC<MaskRevealTextProps> = ({
  text,
  fontSize = 76,
  color = '#FFFFFF',
  fontWeight = 800,
  position,
  positionPreset,
  layoutMode = 'auto',
  revealDirection = 'left_to_right',
  revealFrames = 22,
  textShadow = '0 4px 16px rgba(0,0,0,0.3)',
  letterSpacing = 0,
  textAlign,
  maxWidth = '88%',
  maskPadding = 16,
}) => {
  const frame = useCurrentFrame();
  const { durationInFrames, width, height } = useVideoConfig();
  const layout = resolveTextLayout({
    width,
    height,
    layoutMode,
    position,
    positionPreset,
    portraitDefaultY: '48%',
    landscapeDefaultY: '56%',
    portraitMaxWidth: maxWidth,
    landscapeMaxWidth: typeof maxWidth === 'undefined' ? '76%' : maxWidth,
  });
  const safeDuration = Math.max(3, durationInFrames);
  const safeRevealFrames = Math.min(Math.max(8, revealFrames), Math.max(8, safeDuration - 2));
  const fadeInEnd = Math.min(4, safeDuration - 2);
  const desiredExitStart = Math.min(safeDuration - 1, safeRevealFrames + 12);
  const exitStart = Math.max(fadeInEnd + 1, desiredExitStart);
  const progress = interpolate(frame, [0, safeRevealFrames], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.out(Easing.cubic),
  });
  const opacity = interpolate(frame, [0, fadeInEnd, exitStart, safeDuration], [0, 1, 1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const offsetY = interpolate(progress, [0, 1], [18, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  const clipPath = (() => {
    switch (revealDirection) {
      case 'bottom_to_top':
        return `inset(${(1 - progress) * 100}% -${maskPadding}px -${maskPadding}px -${maskPadding}px)`;
      case 'top_to_bottom':
        return `inset(-${maskPadding}px -${maskPadding}px ${(1 - progress) * 100}% -${maskPadding}px)`;
      case 'right_to_left':
        return `inset(-${maskPadding}px -${maskPadding}px -${maskPadding}px ${(1 - progress) * 100}%)`;
      case 'left_to_right':
      default:
        return `inset(-${maskPadding}px ${(1 - progress) * 100}% -${maskPadding}px -${maskPadding}px)`;
    }
  })();

  return (
    <AbsoluteFill>
      <div
        style={{
          ...layout.wrapperStyle,
          maxWidth: layout.maxWidth,
          overflow: 'hidden',
          clipPath,
          opacity,
          transform: `${layout.wrapperStyle.transform ?? ''} translateY(${offsetY}px)`.trim(),
        }}
      >
        <div
          style={{
            fontFamily,
            fontSize,
            fontWeight,
            color,
            lineHeight: 1.18,
            letterSpacing,
            textAlign: textAlign ?? layout.textAlign,
            textShadow,
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
          }}
        >
          {text}
        </div>
      </div>
    </AbsoluteFill>
  );
};
