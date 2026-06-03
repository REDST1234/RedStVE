/**
 * 预设: media.image — 静态图片嵌入
 */
import React from 'react';
import { Img } from 'remotion';
import { normalizeMediaStyle } from './normalizeMediaStyle';

interface ImageLayerProps {
  src: string;
  style?: React.CSSProperties | Record<string, unknown>;
}

export const ImageLayer: React.FC<ImageLayerProps> = ({ src, style }) => {
  const imgStyle = normalizeMediaStyle(style, 'cover');

  return <Img src={src} style={imgStyle} />;
};
