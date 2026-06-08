/**
 * 预设: text.counter_number — 数字滚动计数器
 * 每个数字位独立纵向滚动，从高位到低位级联触发，类似老虎机转盘
 */
import React from 'react';
import { Easing, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';
import { getFontFamily, FontTier } from '../../fontSystem';
import { resolveTextLayout, LayoutMode } from './layout';

interface CounterNumberProps {
  value: number;
  prefix?: string;
  suffix?: string;
  decimals?: number;
  fontSize?: number;
  color?: string;
  fontWeight?: number;
  scrollFrames?: number;
  digitGap?: number;
  position?: { x: string | number; y: string | number };
  layoutMode?: LayoutMode;
  textShadow?: string;
  letterSpacing?: string | number;
  /** 字体分层，默认 'number'（Bebas Neue 展示数字体） */
  fontTier?: FontTier;
}

/**
 * 单个数字位的滚动容器
 * 当前数字向上滚出并淡出，目标数字从下方滚入并淡入
 */
const RollingDigit: React.FC<{
  digit: string;
  frame: number;
  scrollFrames: number;
  cascadeDelay: number;
  digitIndexFromRight: number;
  color: string;
}> = ({ digit, frame, scrollFrames, cascadeDelay, digitIndexFromRight, color }) => {
  const localFrame = Math.max(0, frame - cascadeDelay);
  const progress = interpolate(localFrame, [0, scrollFrames], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.out(Easing.cubic),
  });

  // 当前数字向上滚动并淡出
  const currentY = interpolate(progress, [0, 1], [0, -56], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const currentOpacity = interpolate(localFrame, [0, scrollFrames * 0.45], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  // 目标数字从下方滚入并淡入
  const nextY = interpolate(progress, [0, 1], [56, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const nextOpacity = interpolate(localFrame, [scrollFrames * 0.55, scrollFrames], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  return (
    <span
      style={{
        display: 'inline-block',
        position: 'relative',
        width: '0.72em',
        height: '1.28em',
        overflow: 'hidden',
        verticalAlign: 'baseline',
      }}
    >
      {/* 当前数字（向上滚出） */}
      <span
        style={{
          position: 'absolute',
          left: 0,
          top: '0.12em',
          width: '100%',
          textAlign: 'center',
          lineHeight: 1,
          color,
          opacity: currentOpacity,
          transform: `translateY(${currentY}px)`,
        }}
      >
        {digit}
      </span>
      {/* 目标数字（从下方滚入） */}
      <span
        style={{
          position: 'absolute',
          left: 0,
          top: '0.12em',
          width: '100%',
          textAlign: 'center',
          lineHeight: 1,
          color,
          opacity: nextOpacity,
          transform: `translateY(${nextY}px)`,
        }}
      >
        {digit}
      </span>
    </span>
  );
};

export const CounterNumber: React.FC<CounterNumberProps> = ({
  value,
  prefix = '',
  suffix = '',
  decimals = 0,
  fontSize = 72,
  color = '#FFFFFF',
  fontWeight = 900,
  scrollFrames = 30,
  digitGap = 4,
  position,
  layoutMode = 'auto',
  textShadow = '0 4px 20px rgba(0,0,0,0.35)',
  letterSpacing = '0.04em',
  fontTier = 'number',
}) => {
  const fontFamily = getFontFamily(fontTier);
  const frame = useCurrentFrame();
  const { width, height } = useVideoConfig();
  const layout = resolveTextLayout({
    width,
    height,
    positionPreset: 'hero_center',
    position: position,
    portraitDefaultY: '42%',
    landscapeDefaultY: '48%',
    layoutMode,
  });

  // 将数值格式化为字符串
  const formatted = value.toFixed(Math.max(0, Math.min(4, decimals)));
  const digits = Array.from(formatted);

  // 级联延迟：从最高位到最低位，每位 delay = scrollFrames / (digitCount * 2)
  const cascadeInterval = Math.max(2, Math.floor(scrollFrames / (Math.max(1, digits.length) * 2.5)));

  // 整体入场透明度
  const enterOpacity = interpolate(frame, [0, Math.min(16, scrollFrames)], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  return (
    <div
      style={{
        ...layout.wrapperStyle,
        fontFamily,
        fontSize,
        fontWeight,
        textShadow,
        letterSpacing,
        opacity: enterOpacity,
        display: 'flex',
        alignItems: 'baseline',
        justifyContent: 'center',
        gap: digitGap,
      }}
    >
      {/* 前缀 */}
      {prefix ? (
        <span style={{ color, opacity: 0.85, fontSize: '0.65em', marginRight: digitGap > 4 ? 0 : -2 }}>
          {prefix}
        </span>
      ) : null}

      {/* 数字位 */}
      {digits.map((char, index) => {
        // 计算该位在从右数第几（跳过小数点计算级联顺序）
        const isFromRight = digits.length - 1 - index;
        // 小数点不滚动
        if (char === '.') {
          return (
            <span
              key={`dot_${index}`}
              style={{
                display: 'inline-block',
                width: '0.38em',
                textAlign: 'center',
                color,
                opacity: enterOpacity,
              }}
            >
              .
            </span>
          );
        }
        // 高位（左侧）先触发
        const cascadeDelay = (digits.length - 1 - isFromRight) * cascadeInterval;
        return (
          <RollingDigit
            key={`${index}_${char}`}
            digit={char}
            frame={frame}
            scrollFrames={scrollFrames}
            cascadeDelay={cascadeDelay}
            digitIndexFromRight={isFromRight}
            color={color}
          />
        );
      })}

      {/* 后缀 */}
      {suffix ? (
        <span style={{ color, opacity: 0.85, fontSize: '0.65em', marginLeft: digitGap > 4 ? 0 : -2 }}>
          {suffix}
        </span>
      ) : null}
    </div>
  );
};
