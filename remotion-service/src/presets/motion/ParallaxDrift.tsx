/**
 * 预设: motion.parallax_drift — 视差漂移
 * 图片沿 X/Y 轴以不同频率正弦漂移，产生李萨如轨迹式的深度移动感。
 * 与 ken_burns 的区别：parallax_drift 是连续循环漂移，没有明确的起始/结束状态。
 */
import React from 'react';
import { AbsoluteFill, Img, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';

interface ParallaxDriftProps {
  src: string;
  driftRangeX?: number;
  driftRangeY?: number;
  driftSpeedX?: number;
  driftSpeedY?: number;
  scaleRange?: number;
  rotationRange?: number;
  objectFit?: 'cover' | 'contain' | 'fill' | 'none';
}

export const ParallaxDrift: React.FC<ParallaxDriftProps> = ({
  src,
  driftRangeX = 30,
  driftRangeY = 20,
  driftSpeedX = 0.5,
  driftSpeedY = 0.7,
  scaleRange = 0.05,
  rotationRange = 1.5,
  objectFit = 'cover',
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // 漂移周期: X 和 Y 使用不同的基础周期产生非同步运动 (李萨如轨迹)
  const basePeriod = fps * 4.0; // ~4 秒基础周期
  const periodX = basePeriod / Math.max(0.3, Math.min(2.0, driftSpeedX));
  const periodY = basePeriod / Math.max(0.3, Math.min(2.0, driftSpeedY));
  const periodScale = basePeriod * 1.3; // 缩放变化更慢
  const periodRotation = basePeriod * 1.1; // 旋转变化介于中间

  const tx = (frame / periodX) * Math.PI * 2;
  const ty = (frame / periodY) * Math.PI * 2;
  const ts = (frame / periodScale) * Math.PI * 2;
  const tr = (frame / periodRotation) * Math.PI * 2;

  // X 轴漂移: sin(tx)
  const translateX = interpolate(Math.sin(tx), [-1, 1], [-driftRangeX, driftRangeX]);
  // Y 轴漂移: cos(ty)，用 cos 避免与 X 同步
  const translateY = interpolate(Math.cos(ty), [-1, 1], [-driftRangeY, driftRangeY]);
  // 缩放波动
  const driftScale = 1 + interpolate(Math.sin(ts), [-1, 1], [-scaleRange, scaleRange]);
  // 旋转微动
  const rotate = interpolate(Math.cos(tr), [-1, 1], [-rotationRange, rotationRange]);

  return (
    <AbsoluteFill style={{ overflow: 'hidden' }}>
      <div
        style={{
          width: '110%',
          height: '110%',
          position: 'absolute',
          top: '-5%',
          left: '-5%',
          transform: `translateX(${translateX}px) translateY(${translateY}px) scale(${driftScale}) rotate(${rotate}deg)`,
          transformOrigin: 'center center',
        }}
      >
        <Img
          src={src}
          style={{
            width: '100%',
            height: '100%',
            objectFit,
          }}
        />
      </div>
    </AbsoluteFill>
  );
};
