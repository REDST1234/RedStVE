/**
 * 预设: text.label_chip — 标签芯片
 * 轻量 pop-in 标签，适合品类标注、特性说明、关键词胶囊
 * 与 overlay.badge_pop 的区别：无旋转、无弹跳、信息属性 > 促销属性
 */
import React from 'react';
import { Easing, interpolate, useCurrentFrame } from 'remotion';
import { loadFont } from '@remotion/google-fonts/NotoSansSC';

const { fontFamily } = loadFont();

type ChipVariant = 'filled' | 'outlined' | 'soft';

interface LabelChipProps {
  text: string;
  variant?: ChipVariant;
  color?: string;
  bgColor?: string;
  borderColor?: string;
  fontSize?: number;
  fontWeight?: number;
  paddingX?: number;
  paddingY?: number;
  borderRadius?: number;
  position?: { x: string | number; y: string | number };
  icon?: string;
  enterFrames?: number;
  textShadow?: string;
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

const resolveChipStyles = (
  variant: ChipVariant,
  color: string,
  bgColor: string,
  borderColor: string,
): { background: string; border: string; textColor: string } => {
  switch (variant) {
    case 'outlined':
      return {
        background: 'transparent',
        border: `1.5px solid ${borderColor || color}`,
        textColor: color,
      };
    case 'soft':
      return {
        background: withAlpha(bgColor, 0.18),
        border: 'none',
        textColor: color,
      };
    case 'filled':
    default:
      return {
        background: bgColor,
        border: 'none',
        textColor: '#FFFFFF',
      };
  }
};

const resolveChipPosition = (
  position?: { x: string | number; y: string | number },
): React.CSSProperties => {
  if (!position) {
    return {
      position: 'absolute',
      left: '50%',
      top: '50%',
      transform: 'translate(-50%, -50%)',
    };
  }

  const style: React.CSSProperties = { position: 'absolute' };
  let transform = '';

  if (position.x === 'center') {
    style.left = '50%';
    transform += 'translateX(-50%)';
  } else if (position.x != null) {
    style.left = position.x;
    if (typeof position.x === 'string' && position.x.trim().endsWith('%')) {
      transform += 'translateX(-50%)';
    }
  }

  transform += ' ';

  if (position.y === 'center') {
    style.top = '50%';
    transform += 'translateY(-50%)';
  } else if (position.y != null) {
    style.top = position.y;
    if (typeof position.y === 'string' && position.y.trim().endsWith('%')) {
      transform += 'translateY(-50%)';
    }
  }

  transform = transform.trim();
  if (transform) {
    style.transform = transform;
  }

  return style;
};

export const LabelChip: React.FC<LabelChipProps> = ({
  text,
  variant = 'filled',
  color = '#FFFFFF',
  bgColor = '#3B82F6',
  borderColor = 'rgba(255,255,255,0.35)',
  fontSize = 28,
  fontWeight = 700,
  paddingX = 16,
  paddingY = 8,
  borderRadius = 999,
  position,
  icon,
  enterFrames = 14,
  textShadow = '0 1px 6px rgba(0,0,0,0.18)',
}) => {
  const frame = useCurrentFrame();

  // 轻量 pop-in：scale 0.85 → 1.0，无旋转无弹跳
  const popProgress = interpolate(frame, [0, enterFrames], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.out(Easing.cubic),
  });
  const scale = interpolate(popProgress, [0, 1], [0.85, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const opacity = interpolate(frame, [0, Math.max(4, enterFrames * 0.35)], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const floatY = interpolate(popProgress, [0, 1], [8, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  const positionStyle = resolveChipPosition(position);

  const { background, border, textColor } = resolveChipStyles(
    variant,
    color,
    bgColor,
    borderColor,
  );

  return (
    <div
      style={{
        ...positionStyle,
        display: 'inline-flex',
        alignItems: 'center',
        gap: icon ? '6px' : '0',
        fontFamily,
        fontSize,
        fontWeight,
        color: textColor,
        background,
        border,
        padding: `${paddingY}px ${paddingX}px`,
        borderRadius,
        opacity,
        transform:
          positionStyle.transform
            ? `${positionStyle.transform} translateY(${floatY}px) scale(${scale})`
            : `translateY(${floatY}px) scale(${scale})`,
        transformOrigin: 'center center',
        textShadow: variant !== 'outlined' ? textShadow : undefined,
        whiteSpace: 'nowrap',
        userSelect: 'none',
      }}
    >
      {icon ? <span style={{ fontSize: '1.1em' }}>{icon}</span> : null}
      {text}
    </div>
  );
};
