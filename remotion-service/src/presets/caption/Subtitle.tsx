/**
 * 预设: caption.subtitle — 底部字幕条
 */
import React from 'react';
import { AbsoluteFill } from 'remotion';
import { getFontFamily, FontTier } from '../../fontSystem';

interface SubtitleProps {
  text: string;
  fontSize?: number;
  color?: string;
  bgColor?: string;
  position?: 'bottom_center' | 'top_center' | 'center';
  /** 字体分层，默认 'subtitle'（Noto Sans SC 高可读性） */
  fontTier?: FontTier;
}

export const Subtitle: React.FC<SubtitleProps> = ({
  text,
  fontSize = 28,
  color = '#FFFFFF',
  bgColor = 'rgba(0,0,0,0.6)',
  position = 'bottom_center',
  fontTier = 'subtitle',
}) => {
  const fontFamily = getFontFamily(fontTier);
  if (!text) return null;

  const positionStyle: React.CSSProperties = (() => {
    switch (position) {
      case 'top_center':
        return { top: 40, left: 0, right: 0 };
      case 'center':
        return { top: '50%', left: 0, right: 0, transform: 'translateY(-50%)' };
      case 'bottom_center':
      default:
        return { bottom: 60, left: 0, right: 0 };
    }
  })();

  return (
    <AbsoluteFill>
      <div
        style={{
          position: 'absolute',
          ...positionStyle,
          display: 'flex',
          justifyContent: 'center',
          padding: '0 20px',
        }}
      >
        <div
          style={{
            fontFamily,
            fontSize,
            color,
            backgroundColor: bgColor,
            padding: '8px 24px',
            borderRadius: 8,
            textAlign: 'center',
            lineHeight: 1.5,
            maxWidth: '80%',
          }}
        >
          {text}
        </div>
      </div>
    </AbsoluteFill>
  );
};
