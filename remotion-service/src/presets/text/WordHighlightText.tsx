/**
 * 预设: text.word_highlight — 逐词高亮文本
 * 适合卖点强调、教程关键词和 CTA 动作词突出
 */
import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';
import { loadFont } from '@remotion/google-fonts/NotoSansSC';
import { LayoutMode, resolveTextLayout, TextPositionPreset } from './layout';

const { fontFamily } = loadFont();

interface WordHighlightTextProps {
  text?: string;
  tokens?: string[];
  highlightWords?: string[];
  fontSize?: number;
  color?: string;
  highlightColor?: string;
  highlightBackground?: string;
  fontWeight?: number;
  position?: { x: string | number; y: string | number };
  positionPreset?: TextPositionPreset;
  layoutMode?: LayoutMode;
  wordDurationInFrames?: number;
  textShadow?: string;
  gap?: number;
  textAlign?: 'left' | 'center' | 'right';
  highlightScale?: number;
}

const normalizeToken = (value: string): string => value.trim().toLowerCase();

export const WordHighlightText: React.FC<WordHighlightTextProps> = ({
  text,
  tokens,
  highlightWords = [],
  fontSize = 50,
  color = '#FFFFFF',
  highlightColor = '#111111',
  highlightBackground = '#FFD54F',
  fontWeight = 800,
  position,
  positionPreset,
  layoutMode = 'auto',
  wordDurationInFrames = 10,
  textShadow = '0 2px 10px rgba(0,0,0,0.4)',
  gap = 10,
  textAlign = 'center',
  highlightScale = 1.08,
}) => {
  const frame = useCurrentFrame();
  const { width, height } = useVideoConfig();
  const layout = resolveTextLayout({
    width,
    height,
    layoutMode,
    position,
    positionPreset,
    portraitDefaultY: '72%',
    landscapeDefaultY: '60%',
    portraitMaxWidth: '86%',
    landscapeMaxWidth: '74%',
  });
  const rawText = text ?? '';
  const derivedTokens =
    tokens && tokens.length > 0
      ? tokens
      : rawText.includes(' ')
        ? rawText.split(/\s+/).filter(Boolean)
        : Array.from(rawText);
  const normalizedHighlights = new Set(highlightWords.map(normalizeToken));
  const eligibleIndexes =
    normalizedHighlights.size === 0
      ? derivedTokens.map((_, index) => index)
      : derivedTokens.flatMap((token, index) =>
          normalizedHighlights.has(normalizeToken(token)) ? [index] : [],
        );
  const safeDuration = Math.max(1, wordDurationInFrames);
  const activeHighlightIndex =
    eligibleIndexes.length === 0
      ? -1
      : eligibleIndexes[Math.min(eligibleIndexes.length - 1, Math.floor(frame / safeDuration))];

  const enterOpacity = interpolate(frame, [0, 8], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  return (
    <AbsoluteFill style={layout.containerStyle}>
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          justifyContent:
            layout.position.x === 'center'
              ? 'center'
              : layout.position.x === 'right'
                ? 'flex-end'
                : 'flex-start',
          gap,
          maxWidth: layout.maxWidth,
          opacity: enterOpacity,
          textAlign,
        }}
      >
        {derivedTokens.map((token, index) => {
          const isActive = index === activeHighlightIndex;
          const activeProgress = isActive ? (frame % safeDuration) / safeDuration : 0;
          const scale = isActive
            ? interpolate(activeProgress, [0, 0.45, 1], [1, highlightScale, 1], {
                extrapolateLeft: 'clamp',
                extrapolateRight: 'clamp',
              })
            : 1;

          return (
            <span
              key={`${token}_${index}`}
              style={{
                fontFamily,
                fontSize,
                fontWeight,
                color: isActive ? highlightColor : color,
                background: isActive ? highlightBackground : 'transparent',
                padding: isActive ? '4px 12px' : '4px 0',
                borderRadius: isActive ? 999 : 0,
                lineHeight: 1.3,
                textShadow,
                transform: `scale(${scale})`,
                opacity: isActive ? 1 : 0.78,
              }}
            >
              {token}
            </span>
          );
        })}
      </div>
    </AbsoluteFill>
  );
};
