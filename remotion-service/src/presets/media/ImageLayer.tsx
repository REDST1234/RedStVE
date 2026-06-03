/**
 * 预设: media.image — 静态图片嵌入
 */
import React from 'react';
import { Img } from 'remotion';

interface ImageLayerProps {
  src: string;
  style?: React.CSSProperties;
}

export const ImageLayer: React.FC<ImageLayerProps> = ({ src, style }) => {
  const imgStyle: React.CSSProperties = {
    width: '100%',
    height: '100%',
    objectFit: 'cover' as const,
    ...style,
  };

  return <Img src={src} style={imgStyle} />;
};
