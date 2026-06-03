/**
 * 预设: overlay.flash — 短时闪光冲击层
 * 适合高潮点、价格强调、转折冲击
 */
import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';

interface FlashOverlayProps {
  color?: string;
  maxOpacity?: number;
  enterFrames?: number;
  holdFrames?: number;
  exitFrames?: number;
}

export const FlashOverlay: React.FC<FlashOverlayProps> = ({
  color = '#FFFFFF',
  maxOpacity = 0.92,
  enterFrames = 3,
  holdFrames = 2,
  exitFrames = 8,
}) => {
  const frame = useCurrentFrame();
  const { durationInFrames } = useVideoConfig();
  const total = Math.max(1, durationInFrames);
  const fadeIn = Math.max(1, Math.min(enterFrames, total));
  const holdEnd = Math.min(total, fadeIn + Math.max(0, holdFrames));
  const fadeOutEnd = Math.min(total, holdEnd + Math.max(1, exitFrames));

  const opacity = interpolate(
    frame,
    [0, fadeIn, holdEnd, fadeOutEnd],
    [0, maxOpacity, maxOpacity, 0],
    {
      extrapolateLeft: 'clamp',
      extrapolateRight: 'clamp',
    },
  );

  return (
    <AbsoluteFill
      style={{
        backgroundColor: color,
        opacity,
        mixBlendMode: 'screen',
      }}
    />
  );
};
