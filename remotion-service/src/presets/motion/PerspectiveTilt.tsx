/**
 * 预设: motion.perspective_tilt — 透视倾斜
 * CSS 3D perspective + rotateX/Y 实现卡片式 3D 透视倾斜效果。
 * 类似 Apple TV 卡片悬停或 iOS App Store 卡片风格。
 * 可开启 dynamic 模式实现缓慢呼吸式微动。
 */
import React from 'react';
import { AbsoluteFill, Img, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';

interface PerspectiveTiltProps {
  src: string;
  rotateX?: number;
  rotateY?: number;
  perspective?: number;
  scale?: number;
  dynamicEnabled?: boolean;
  dynamicRange?: number;
  shadowEnabled?: boolean;
  shadowColor?: string;
  objectFit?: 'cover' | 'contain' | 'fill' | 'none';
}

export const PerspectiveTilt: React.FC<PerspectiveTiltProps> = ({
  src,
  rotateX = -5,
  rotateY = 3,
  perspective = 1000,
  scale = 0.85,
  dynamicEnabled = true,
  dynamicRange = 2,
  shadowEnabled = true,
  shadowColor = 'rgba(0,0,0,0.3)',
  objectFit = 'contain',
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  let dynamicX = 0;
  let dynamicY = 0;

  if (dynamicEnabled) {
    // 缓慢微动：X 和 Y 使用不同周期
    const period = fps * 5.0; // ~5 秒一个周期
    const tx = (frame / period) * Math.PI * 2;
    const ty = (frame / (period * 1.3)) * Math.PI * 2; // Y 轴偏移相位
    dynamicX = interpolate(Math.sin(tx), [-1, 1], [-dynamicRange, dynamicRange]);
    dynamicY = interpolate(Math.cos(ty), [-1, 1], [-dynamicRange, dynamicRange]);
  }

  const finalRotateX = rotateX + dynamicX;
  const finalRotateY = rotateY + dynamicY;

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
    transform: `rotateX(${finalRotateX}deg) rotateY(${finalRotateY}deg) scale(${scale})`,
    transformStyle: 'preserve-3d',
    position: 'relative',
  };

  const imgStyle: React.CSSProperties = {
    width: '100%',
    height: '100%',
    objectFit,
    display: 'block',
    borderRadius: '12px',
  };

  // 静态深度阴影增强 3D 感
  const boxShadow = shadowEnabled
    ? `0 20px 60px ${shadowColor}, 0 8px 24px ${shadowColor.replace(/[\d.]+\)$/, '0.15)')}`
    : 'none';

  return (
    <AbsoluteFill style={containerStyle}>
      <div
        style={{
          ...cardStyle,
          boxShadow,
        }}
      >
        <Img src={src} style={imgStyle} />
      </div>
    </AbsoluteFill>
  );
};
