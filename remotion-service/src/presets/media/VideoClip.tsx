/**
 * 预设: media.video — 视频片段嵌入
 * 支持裁切、音量、变速、循环、样式
 */
import React from 'react';
import { Video } from '@remotion/media';
import { useVideoConfig } from 'remotion';
import { normalizeMediaStyle } from './normalizeMediaStyle';

interface VideoClipProps {
  src: string;
  trimBefore?: number;
  trimAfter?: number;
  volume?: number;
  playbackRate?: number;
  loop?: boolean;
  muted?: boolean;
  style?: React.CSSProperties | Record<string, unknown>;
}

export const VideoClip: React.FC<VideoClipProps> = ({
  src,
  trimBefore,
  trimAfter,
  volume = 1,
  playbackRate = 1,
  loop = false,
  muted = false,
  style,
}) => {
  const { fps: _fps } = useVideoConfig();

  const videoStyle = normalizeMediaStyle(style, 'cover');

  return (
    <Video
      src={src}
      style={videoStyle}
      volume={muted ? 0 : volume}
      playbackRate={playbackRate}
      loop={loop}
      muted={muted}
      trimBefore={trimBefore}
      trimAfter={trimAfter}
    />
  );
};
