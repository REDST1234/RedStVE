/**
 * 预设: motion.ken_burns — 图片伪运镜（推/拉/平移）
 * 通过 scale + translate 的 interpolate 实现 Ken Burns 效果
 */
import React from 'react';
import { AbsoluteFill, Img, interpolate, useCurrentFrame, useVideoConfig, Easing } from 'remotion';

interface KenBurnsProps {
  src: string;
  startScale?: number;
  endScale?: number;
  startPosition?: { x: number; y: number };
  endPosition?: { x: number; y: number };
  easing?: [number, number, number, number];
}

export const KenBurns: React.FC<KenBurnsProps> = ({
  src,
  startScale = 1.0,
  endScale = 1.2,
  startPosition = { x: 0, y: 0 },
  endPosition = { x: 0, y: 0 },
  easing = [0.45, 0, 0.55, 1],
}) => {
  const frame = useCurrentFrame();
  const { durationInFrames } = useVideoConfig();

  const progress = interpolate(frame, [0, durationInFrames], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.bezier(easing[0], easing[1], easing[2], easing[3]),
  });

  const scale = interpolate(progress, [0, 1], [startScale, endScale]);
  const translateX = interpolate(progress, [0, 1], [startPosition.x, endPosition.x]);
  const translateY = interpolate(progress, [0, 1], [startPosition.y, endPosition.y]);

  return (
    <AbsoluteFill style={{ overflow: 'hidden' }}>
      <Img
        src={src}
        style={{
          width: '100%',
          height: '100%',
          objectFit: 'cover',
          transform: `scale(${scale}) translate(${translateX}px, ${translateY}px)`,
        }}
      />
    </AbsoluteFill>
  );
};
