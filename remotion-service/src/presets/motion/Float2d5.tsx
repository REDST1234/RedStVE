/**
 * 预设: motion.float_2d5 — 2.5D 漂浮卡片
 * 通过正弦振荡实现上下浮动、左右摇摆和深度阴影变化，模拟悬浮在 3D 空间中的卡片
 */
import React from 'react';
import { AbsoluteFill, Img, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';

interface Float2d5Props {
  src: string;
  floatAmplitude?: number;
  floatSpeed?: number;
  swayAmount?: number;
  scaleBreath?: number;
  perspective?: number;
  shadowEnabled?: boolean;
  shadowColor?: string;
  objectFit?: 'cover' | 'contain' | 'fill' | 'none';
  scale?: number;
}

export const Float2d5: React.FC<Float2d5Props> = ({
  src,
  floatAmplitude = 12,
  floatSpeed = 0.7,
  swayAmount = 2,
  scaleBreath = 0.03,
  perspective = 800,
  shadowEnabled = true,
  shadowColor = 'rgba(0,0,0,0.25)',
  objectFit = 'contain',
  scale = 0.9,
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // 浮动周期基数 (帧): 速度越高，周期越短
  const basePeriodFrames = fps * 3.5; // ~3.5 秒一个完整周期
  const adjustedPeriod = basePeriodFrames / Math.max(0.3, Math.min(2.0, floatSpeed));

  // 进度：使用正弦波，确保动画循环平滑
  const t = (frame / adjustedPeriod) * Math.PI * 2;

  // 上下浮动: sin(t)
  const translateY = interpolate(Math.sin(t), [-1, 1], [-floatAmplitude, floatAmplitude]);

  // 左右摇摆: sin(t + π/3)，相位偏移避免与浮动同步
  const swayPhase = t + Math.PI / 3;
  const rotateZ = interpolate(Math.sin(swayPhase), [-1, 1], [-swayAmount, swayAmount]);

  // 缩放呼吸: sin(t + π/2)，再次偏移相位
  const breathPhase = t + Math.PI / 2;
  const breathScale = 1 + interpolate(Math.sin(breathPhase), [-1, 1], [-scaleBreath, scaleBreath]);

  // 动态阴影: 浮动越高阴影越远越淡，浮动越低阴影越近越深
  const shadowOffsetY = interpolate(Math.sin(t), [-1, 1], [8, 28]);
  const shadowBlur = interpolate(Math.sin(t), [-1, 1], [24, 48]);
  const shadowAlpha = interpolate(Math.sin(t), [-1, 1], [0.15, 0.35]);

  const finalScale = scale * breathScale;

  const containerStyle: React.CSSProperties = {
    width: '100%',
    height: '100%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    perspective: `${perspective}px`,
    transformStyle: 'preserve-3d',
  };

  const cardStyle: React.CSSProperties = {
    transform: `rotateX(4deg) translateY(${translateY}px) rotateZ(${rotateZ}deg) scale(${finalScale})`,
    transformStyle: 'preserve-3d',
    position: 'relative',
  };

  const imgStyle: React.CSSProperties = {
    width: '100%',
    height: '100%',
    objectFit,
    display: 'block',
  };

  // 使用 filter 模拟动态阴影 (offsetY + blur + alpha)
  const shadowFilter = shadowEnabled
    ? `drop-shadow(0 ${shadowOffsetY}px ${shadowBlur}px ${shadowColor.replace(/[\d.]+\)$/, `${shadowAlpha.toFixed(2)})`)})`
    : 'none';

  return (
    <AbsoluteFill style={containerStyle}>
      <div style={cardStyle}>
        <Img
          src={src}
          style={{
            ...imgStyle,
            filter: shadowFilter,
          }}
        />
      </div>
    </AbsoluteFill>
  );
};
