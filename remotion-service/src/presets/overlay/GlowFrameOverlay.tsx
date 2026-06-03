/**
 * 预设: overlay.glow_frame — 边框高光层
 * 适合主体强调、推荐片段和高光卡段
 */
import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion';

interface GlowFrameOverlayProps {
  color?: string;
  thickness?: number;
  glowBlur?: number;
  opacity?: number;
  borderRadius?: number;
  pulseStrength?: number;
  inset?: number;
}

export const GlowFrameOverlay: React.FC<GlowFrameOverlayProps> = ({
  color = '#55E6C1',
  thickness = 10,
  glowBlur = 24,
  opacity = 0.82,
  borderRadius = 28,
  pulseStrength = 0.16,
  inset = 24,
}) => {
  const frame = useCurrentFrame();
  const pulse = interpolate(frame % 32, [0, 16, 31], [0, pulseStrength, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  return (
    <AbsoluteFill>
      <div
        style={{
          position: 'absolute',
          inset,
          borderRadius,
          border: `${thickness}px solid ${color}`,
          opacity: Math.min(1, opacity + pulse),
          boxShadow: `0 0 ${glowBlur}px ${color}, inset 0 0 ${Math.max(12, glowBlur / 2)}px ${color}`,
        }}
      />
    </AbsoluteFill>
  );
};
