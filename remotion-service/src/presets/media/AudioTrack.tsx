/**
 * 预设: media.audio — BGM / 音效嵌入
 * 支持音量渐入渐出、裁切、循环
 */
import React from 'react';
import { Audio } from '@remotion/media';
import { interpolate, useVideoConfig } from 'remotion';

interface AudioTrackProps {
  src: string;
  volume?: number;
  loop?: boolean;
  trimBefore?: number;
  trimAfter?: number;
  fadeInFrames?: number;
  fadeOutFrames?: number;
  totalDurationFrames?: number;
}

export const AudioTrack: React.FC<AudioTrackProps> = ({
  src,
  volume = 1,
  loop = false,
  trimBefore,
  trimAfter,
  fadeInFrames = 0,
  fadeOutFrames = 0,
  totalDurationFrames,
}) => {
  const { durationInFrames } = useVideoConfig();
  const total = totalDurationFrames ?? durationInFrames;

  const volumeCurve = (frame: number): number => {
    let v = volume;
    // 渐入
    if (fadeInFrames > 0 && frame < fadeInFrames) {
      v *= interpolate(frame, [0, fadeInFrames], [0, 1], {
        extrapolateLeft: 'clamp',
        extrapolateRight: 'clamp',
      });
    }
    // 渐出
    if (fadeOutFrames > 0 && frame > total - fadeOutFrames) {
      v *= interpolate(frame, [total - fadeOutFrames, total], [1, 0], {
        extrapolateLeft: 'clamp',
        extrapolateRight: 'clamp',
      });
    }
    return v;
  };

  return (
    <Audio
      src={src}
      volume={volumeCurve}
      loop={loop}
      trimBefore={trimBefore}
      trimAfter={trimAfter}
    />
  );
};
