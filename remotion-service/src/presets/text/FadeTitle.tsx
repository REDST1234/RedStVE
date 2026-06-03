/**
 * 预设: text.fade_title — 标题卡淡入淡出
 * 通过 opacity + translateY 的 interpolate 实现优雅入场
 */
import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig, Easing } from 'remotion';
import { loadFont } from '@remotion/google-fonts/NotoSansSC';
import { LayoutMode, resolveTextLayout, TextPositionPreset } from './layout';

const { fontFamily } = loadFont();

interface FadeTitleProps {
  text: string;
  fontSize?: number;
  color?: string;
  fontWeight?: number;
  position?: { x: string | number; y: string | number };
  positionPreset?: TextPositionPreset;
  layoutMode?: LayoutMode;
  easing?: [number, number, number, number];
  textShadow?: string;
  maxWidth?: string | number;
}

export const FadeTitle: React.FC<FadeTitleProps> = ({
  text,
  fontSize = 48,
  color = '#FFFFFF',
  fontWeight = 700,
  position,
  positionPreset,
  layoutMode = 'auto',
  easing = [0.16, 1, 0.3, 1],
  textShadow = '0 2px 10px rgba(0,0,0,0.5)',
  maxWidth,
}) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames, width, height } = useVideoConfig();
  const layout = resolveTextLayout({
    width,
    height,
    layoutMode,
    position,
    positionPreset,
    portraitDefaultY: '50%',
    landscapeDefaultY: '56%',
    portraitMaxWidth: maxWidth ?? '86%',
    landscapeMaxWidth: maxWidth ?? '74%',
  });

  const fadeInEnd = Math.min(fps, durationInFrames);
  const fadeOutStart = Math.max(0, durationInFrames - fps);

  // 入场动画
  const enterOpacity = interpolate(frame, [0, fadeInEnd], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.bezier(easing[0], easing[1], easing[2], easing[3]),
  });

  const enterY = interpolate(frame, [0, fadeInEnd], [20, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.bezier(easing[0], easing[1], easing[2], easing[3]),
  });

  // 出场动画
  const exitOpacity = interpolate(frame, [fadeOutStart, durationInFrames], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  const opacity = Math.min(enterOpacity, exitOpacity);

  return (
    <AbsoluteFill>
      <div
        style={{
          ...layout.wrapperStyle,
          fontFamily,
          fontSize,
          fontWeight,
          color,
          textShadow,
          opacity,
          transform: `${layout.wrapperStyle.transform ?? ''} translateY(${enterY}px)`.trim(),
          textAlign: layout.textAlign,
          padding: '0 40px',
          maxWidth: layout.maxWidth,
          lineHeight: 1.4,
        }}
      >
        {text}
      </div>
    </AbsoluteFill>
  );
};
