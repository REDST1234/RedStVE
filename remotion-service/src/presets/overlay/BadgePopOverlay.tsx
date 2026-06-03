/**
 * 预设: overlay.badge_pop — 角标/卖点标签弹出
 * 适合新品、限时、卖点补全等短文字包装
 */
import React from 'react';
import { AbsoluteFill, Easing, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';
import { loadFont } from '@remotion/google-fonts/NotoSansSC';

const { fontFamily } = loadFont();

interface BadgePopOverlayProps {
  text: string;
  bgColor?: string;
  color?: string;
  fontSize?: number;
  fontWeight?: number;
  position?: { x: string | number; y: string | number };
  scaleFrom?: number;
  scaleTo?: number;
  rotation?: number;
  paddingX?: number;
  paddingY?: number;
  borderRadius?: number;
  shadowColor?: string;
  borderColor?: string;
}

export const BadgePopOverlay: React.FC<BadgePopOverlayProps> = ({
  text,
  bgColor = '#FF6B35',
  color = '#FFFFFF',
  fontSize = 34,
  fontWeight = 800,
  position = { x: '8%', y: '14%' },
  scaleFrom = 0.78,
  scaleTo = 1,
  rotation = -6,
  paddingX = 24,
  paddingY = 14,
  borderRadius = 999,
  shadowColor = 'rgba(0,0,0,0.22)',
  borderColor = 'rgba(255,255,255,0.28)',
}) => {
  const frame = useCurrentFrame();
  const { durationInFrames } = useVideoConfig();
  const enterFrames = Math.min(16, Math.max(6, Math.floor(durationInFrames * 0.28)));
  const exitStart = Math.max(enterFrames + 6, durationInFrames - 10);

  const popProgress = interpolate(frame, [0, enterFrames], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.out(Easing.back(1.4)),
  });
  const scale = interpolate(popProgress, [0, 1], [scaleFrom, scaleTo], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const opacity = interpolate(frame, [0, 4, exitStart, durationInFrames], [0, 1, 1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const floatY = interpolate(frame, [0, enterFrames], [20, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  return (
    <AbsoluteFill>
      <div
        style={{
          position: 'absolute',
          left: position.x,
          top: position.y,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontFamily,
          fontSize,
          fontWeight,
          color,
          backgroundColor: bgColor,
          padding: `${paddingY}px ${paddingX}px`,
          borderRadius,
          border: `2px solid ${borderColor}`,
          boxShadow: `0 16px 40px ${shadowColor}`,
          textShadow: '0 1px 6px rgba(0,0,0,0.28)',
          opacity,
          transform: `translateY(${floatY}px) rotate(${rotation}deg) scale(${scale})`,
          transformOrigin: 'center center',
          whiteSpace: 'nowrap',
        }}
      >
        {text}
      </div>
    </AbsoluteFill>
  );
};
