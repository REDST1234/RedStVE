/**
 * 预设: text.hero_billboard — 多版式大字报组件
 * 适合 MG hook、口号短句、卖点三连、四角散点等强排版场景
 */
import React from 'react';
import { AbsoluteFill, Easing, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';
import { getFontFamily, FontTier } from '../../fontSystem';
import { LayoutMode } from './layout';

type LayoutPattern =
  | 'center_focus'
  | 'four_corners'
  | 'triangle_stack'
  | 'top_bottom_split'
  | 'left_right_balance';
type AnimationMode = 'whole_pop' | 'char_stagger' | 'type_reveal';
type EasingPreset = 'spring_bounce' | 'expo_out' | 'linear_fade';

interface HeroBillboardTextProps {
  text?: string;
  texts?: string[];
  layoutPattern?: LayoutPattern;
  animationMode?: AnimationMode;
  easingPreset?: EasingPreset;
  layoutMode?: LayoutMode;
  fontSize?: number;
  color?: string;
  accentColor?: string;
  fontWeight?: number;
  letterSpacing?: string | number;
  textShadow?: string;
  strokeEnabled?: boolean;
  strokeColor?: string;
  strokeWidth?: number;
  glowColor?: string;
  glowBlur?: number;
  glowOpacity?: number;
  maxWidth?: string | number;
  charIntervalFrames?: number;
  staggerFrames?: number;
  lineGap?: number;
  /** 字体分层，默认 'title'（ZCOOL XiaoWei） */
  fontTier?: FontTier;
}

interface BillboardPosition {
  x: string;
  y: string;
  align: 'left' | 'center' | 'right';
  maxWidth?: string | number;
}

const buildPositions = (
  pattern: LayoutPattern,
  itemCount: number,
  isLandscape: boolean,
): BillboardPosition[] => {
  if (pattern === 'four_corners') {
    const presets: BillboardPosition[] = isLandscape
      ? [
          { x: '14%', y: '18%', align: 'left' },
          { x: '86%', y: '18%', align: 'right' },
          { x: '14%', y: '74%', align: 'left' },
          { x: '86%', y: '74%', align: 'right' },
        ]
      : [
          { x: '14%', y: '22%', align: 'left' },
          { x: '86%', y: '22%', align: 'right' },
          { x: '14%', y: '72%', align: 'left' },
          { x: '86%', y: '72%', align: 'right' },
        ];
    return presets.slice(0, Math.max(1, Math.min(itemCount, presets.length)));
  }

  if (pattern === 'triangle_stack') {
    const presets: BillboardPosition[] = isLandscape
      ? [
          { x: '30%', y: '28%', align: 'center', maxWidth: '30%' },
          { x: '70%', y: '28%', align: 'center', maxWidth: '30%' },
          { x: '50%', y: '64%', align: 'center', maxWidth: '42%' },
        ]
      : [
          { x: '28%', y: '28%', align: 'center', maxWidth: '34%' },
          { x: '72%', y: '28%', align: 'center', maxWidth: '34%' },
          { x: '50%', y: '62%', align: 'center', maxWidth: '52%' },
        ];
    return presets.slice(0, Math.max(1, Math.min(itemCount, presets.length)));
  }

  if (pattern === 'top_bottom_split') {
    const presets: BillboardPosition[] = isLandscape
      ? [
          { x: '50%', y: '26%', align: 'center', maxWidth: '68%' },
          { x: '50%', y: '68%', align: 'center', maxWidth: '72%' },
        ]
      : [
          { x: '50%', y: '24%', align: 'center', maxWidth: '82%' },
          { x: '50%', y: '70%', align: 'center', maxWidth: '84%' },
        ];
    return presets.slice(0, Math.max(1, Math.min(itemCount, presets.length)));
  }

  if (pattern === 'left_right_balance') {
    const presets: BillboardPosition[] = isLandscape
      ? [
          { x: '12%', y: '46%', align: 'left', maxWidth: '32%' },
          { x: '88%', y: '46%', align: 'right', maxWidth: '32%' },
        ]
      : [
          { x: '10%', y: '40%', align: 'left', maxWidth: '34%' },
          { x: '90%', y: '62%', align: 'right', maxWidth: '34%' },
        ];
    return presets.slice(0, Math.max(1, Math.min(itemCount, presets.length)));
  }

  if (itemCount <= 1) {
    return [{ x: '50%', y: isLandscape ? '48%' : '44%', align: 'center' }];
  }

  const startY = isLandscape ? 40 : 34;
  const gap = isLandscape ? 14 : 12;
  return Array.from({ length: itemCount }, (_, index) => ({
    x: '50%',
    y: `${startY + index * gap}%`,
    align: 'center',
  }));
};

const getEasing = (preset: EasingPreset) => {
  switch (preset) {
    case 'expo_out':
      return Easing.out(Easing.exp);
    case 'linear_fade':
      return Easing.linear;
    case 'spring_bounce':
    default:
      return Easing.out(Easing.back(1.4));
  }
};

const withAlpha = (color: string, opacity: number): string => {
  const normalized = color.trim();
  const clamped = Math.max(0, Math.min(1, opacity));
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

const renderAnimatedText = ({
  text,
  frame,
  animationMode,
  charIntervalFrames,
  color,
  accentColor,
}: {
  text: string;
  frame: number;
  animationMode: AnimationMode;
  charIntervalFrames: number;
  color: string;
  accentColor: string;
}) => {
  if (animationMode === 'type_reveal') {
    const visible = Math.min(text.length, Math.floor(frame / Math.max(1, charIntervalFrames)));
    return (
      <>
        <span>{text.slice(0, visible)}</span>
        {visible < text.length ? (
          <span style={{ color: accentColor, marginLeft: 2, opacity: 0.8 }}>|</span>
        ) : null}
      </>
    );
  }

  if (animationMode === 'char_stagger') {
    return (
      <>
        {Array.from(text).map((char, index) => {
          const charStart = index * Math.max(1, Math.floor(charIntervalFrames / 2));
          const charOpacity = interpolate(frame, [charStart, charStart + 6], [0, 1], {
            extrapolateLeft: 'clamp',
            extrapolateRight: 'clamp',
          });
          const charY = interpolate(frame, [charStart, charStart + 8], [12, 0], {
            extrapolateLeft: 'clamp',
            extrapolateRight: 'clamp',
          });
          return (
            <span
              key={`${char}_${index}`}
              style={{
                display: 'inline-block',
                opacity: charOpacity,
                transform: `translateY(${charY}px)`,
                color: index % 2 === 1 ? accentColor : color,
              }}
            >
              {char}
            </span>
          );
        })}
      </>
    );
  }

  return text;
};

export const HeroBillboardText: React.FC<HeroBillboardTextProps> = ({
  text,
  texts,
  layoutPattern = 'center_focus',
  animationMode = 'whole_pop',
  easingPreset = 'spring_bounce',
  layoutMode = 'auto',
  fontSize = 108,
  color = '#FFFFFF',
  accentColor = '#7DD3FC',
  fontWeight = 900,
  letterSpacing = '0.18em',
  textShadow = '0 0 18px rgba(255,255,255,0.18), 0 6px 28px rgba(0,0,0,0.45)',
  strokeEnabled = false,
  strokeColor = '#0B1120',
  strokeWidth = 2.2,
  glowColor = '#7DD3FC',
  glowBlur = 26,
  glowOpacity = 0.28,
  maxWidth = '88%',
  charIntervalFrames = 2,
  staggerFrames = 8,
  lineGap = 18,
  fontTier = 'title',
}) => {
  const fontFamily = getFontFamily(fontTier);
  const frame = useCurrentFrame();
  const { width, height } = useVideoConfig();
  const isLandscape = layoutMode === 'landscape' || (layoutMode === 'auto' && width > height);
  const normalizedTexts = (texts && texts.length > 0 ? texts : text ? [text] : [])
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(
      0,
      layoutPattern === 'four_corners'
        ? 4
        : layoutPattern === 'triangle_stack'
          ? 3
          : layoutPattern === 'top_bottom_split' || layoutPattern === 'left_right_balance'
            ? 2
            : 4,
    );

  if (normalizedTexts.length === 0) {
    return null;
  }

  const positions = buildPositions(layoutPattern, normalizedTexts.length, isLandscape);
  const easing = getEasing(easingPreset);
  const resolvedFontSize = isLandscape ? Math.round(fontSize * 0.92) : fontSize;
  const resolvedLineGap = isLandscape ? Math.max(12, lineGap - 4) : lineGap;
  const effectiveStrokeWidth = strokeEnabled ? Math.max(0.8, strokeWidth) : 0;
  const resolvedTextShadow = `${textShadow}, 0 0 ${glowBlur}px ${withAlpha(glowColor, glowOpacity)}`;

  return (
    <AbsoluteFill>
      {normalizedTexts.map((item, index) => {
        const pos = positions[Math.min(index, positions.length - 1)];
        const layerStart = index * Math.max(4, staggerFrames);
        const opacity = interpolate(frame, [layerStart, layerStart + 8], [0, 1], {
          extrapolateLeft: 'clamp',
          extrapolateRight: 'clamp',
          easing,
        });
        const scale = animationMode === 'whole_pop'
          ? interpolate(frame, [layerStart, layerStart + 14], [1.18, 1], {
              extrapolateLeft: 'clamp',
              extrapolateRight: 'clamp',
              easing,
            })
          : 1;
        const translateY = interpolate(frame, [layerStart, layerStart + 12], [18, 0], {
          extrapolateLeft: 'clamp',
          extrapolateRight: 'clamp',
          easing,
        });

        return (
          <div
            key={`${item}_${index}`}
            style={{
              position: 'absolute',
              left: pos.align === 'left' || pos.align === 'center' ? pos.x : undefined,
              right: pos.align === 'right' ? `${100 - Number.parseFloat(pos.x)}%` : undefined,
              top: pos.y,
              transform:
                pos.align === 'center'
                  ? `translate(-50%, 0) translateY(${translateY}px) scale(${scale})`
                  : `translateY(${translateY}px) scale(${scale})`,
              transformOrigin: 'center center',
              maxWidth: pos.maxWidth ?? maxWidth,
              textAlign: pos.align,
              opacity,
              lineHeight: 1.08,
              letterSpacing,
              fontFamily,
              fontSize: resolvedFontSize,
              fontWeight,
              color,
              textShadow: resolvedTextShadow,
              WebkitTextStroke: effectiveStrokeWidth > 0 ? `${effectiveStrokeWidth}px ${strokeColor}` : undefined,
              paintOrder: effectiveStrokeWidth > 0 ? 'stroke fill' : undefined,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
              display: 'flex',
              flexDirection: 'column',
              gap: resolvedLineGap,
            }}
          >
            <div>
              {renderAnimatedText({
                text: item,
                frame: Math.max(0, frame - layerStart),
                animationMode,
                charIntervalFrames,
                color,
                accentColor,
              })}
            </div>
          </div>
        );
      })}
    </AbsoluteFill>
  );
};
