/**
 * 预设: text.typewriter — 打字机标题
 * 适合 hook 标题、教程步骤标题和 CTA 前的短句强调
 */
import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig, Easing } from 'remotion';
import { loadFont } from '@remotion/google-fonts/NotoSansSC';
import { LayoutMode, resolveTextLayout, TextPositionPreset } from './layout';

const { fontFamily } = loadFont();

interface TypewriterTitleProps {
  text: string;
  fontSize?: number;
  color?: string;
  fontWeight?: number;
  position?: { x: string | number; y: string | number };
  positionPreset?: TextPositionPreset;
  layoutMode?: LayoutMode;
  charIntervalFrames?: number;
  cursor?: string;
  cursorColor?: string;
  cursorScale?: number;
  textShadow?: string;
  maxWidth?: string | number;
  letterSpacing?: string | number;
  textAlign?: 'left' | 'center' | 'right';
}

export const TypewriterTitle: React.FC<TypewriterTitleProps> = ({
  text,
  fontSize = 56,
  color = '#FFFFFF',
  fontWeight = 800,
  position,
  positionPreset,
  layoutMode = 'auto',
  charIntervalFrames = 2,
  cursor = '|',
  cursorColor = '#FFFFFF',
  cursorScale = 0.72,
  textShadow = '0 2px 10px rgba(0,0,0,0.45)',
  maxWidth = '82%',
  letterSpacing = 0,
  textAlign = 'center',
}) => {
  const frame = useCurrentFrame();
  const { durationInFrames, width, height } = useVideoConfig();
  const layout = resolveTextLayout({
    width,
    height,
    layoutMode,
    position,
    positionPreset,
    portraitDefaultY: '34%',
    landscapeDefaultY: '42%',
    portraitMaxWidth: maxWidth,
    landscapeMaxWidth: typeof maxWidth === 'undefined' ? '70%' : maxWidth,
  });
  const safeText = text ?? '';
  const safeInterval = Math.max(1, charIntervalFrames);
  const visibleChars = Math.min(safeText.length, Math.floor(frame / safeInterval));
  const displayedText = safeText.slice(0, visibleChars);
  const showCursor = visibleChars < safeText.length || frame < durationInFrames - 8;

  const enterOpacity = interpolate(frame, [0, 8], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.bezier(0.2, 0.8, 0.2, 1),
  });
  const cursorOpacity = showCursor
    ? interpolate(frame % 18, [0, 8, 17], [1, 0.25, 1], {
        extrapolateLeft: 'clamp',
        extrapolateRight: 'clamp',
      })
    : 0;

  return (
    <AbsoluteFill style={layout.containerStyle}>
      <div
        style={{
          display: 'inline-flex',
          alignItems: 'baseline',
          maxWidth: layout.maxWidth,
          fontFamily,
          fontSize,
          fontWeight,
          color,
          letterSpacing,
          lineHeight: 1.25,
          textAlign,
          textShadow,
          opacity: enterOpacity,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}
      >
        <span>{displayedText}</span>
        <span
          style={{
            marginLeft: 2,
            color: cursorColor,
            opacity: cursorOpacity,
            transform: `scale(${cursorScale})`,
            transformOrigin: 'left center',
            display: 'inline-block',
          }}
        >
          {cursor}
        </span>
      </div>
    </AbsoluteFill>
  );
};
