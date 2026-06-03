import React from 'react';

export type LayoutMode = 'auto' | 'portrait' | 'landscape';
export type TextPositionPreset =
  | 'hero_top'
  | 'hero_center'
  | 'hero_lower'
  | 'left_focus'
  | 'right_focus';

export interface TextLayoutOptions {
  width: number;
  height: number;
  layoutMode?: LayoutMode;
  position?: { x: string | number; y: string | number };
  positionPreset?: TextPositionPreset;
  portraitDefaultY: string;
  landscapeDefaultY: string;
  portraitMaxWidth?: string | number;
  landscapeMaxWidth?: string | number;
}

export interface ResolvedTextLayout {
  isLandscape: boolean;
  position: { x: string | number; y: string | number };
  containerStyle: React.CSSProperties;
  maxWidth?: string | number;
}

const presetToPosition = (
  preset: TextPositionPreset,
  isLandscape: boolean,
): { x: string | number; y: string | number } => {
  switch (preset) {
    case 'hero_top':
      return { x: 'center', y: isLandscape ? '28%' : '24%' };
    case 'hero_center':
      return { x: 'center', y: isLandscape ? '48%' : '42%' };
    case 'hero_lower':
      return { x: 'center', y: isLandscape ? '66%' : '60%' };
    case 'left_focus':
      return { x: 'left', y: isLandscape ? '40%' : '34%' };
    case 'right_focus':
      return { x: 'right', y: isLandscape ? '40%' : '34%' };
    default:
      return { x: 'center', y: isLandscape ? '48%' : '42%' };
  }
};

export const resolveTextLayout = ({
  width,
  height,
  layoutMode = 'auto',
  position,
  positionPreset,
  portraitDefaultY,
  landscapeDefaultY,
  portraitMaxWidth = '86%',
  landscapeMaxWidth = '72%',
}: TextLayoutOptions): ResolvedTextLayout => {
  const isLandscape = layoutMode === 'landscape' || (layoutMode === 'auto' && width > height);
  const resolvedPosition =
    position ??
    (positionPreset
      ? presetToPosition(positionPreset, isLandscape)
      : { x: 'center', y: isLandscape ? landscapeDefaultY : portraitDefaultY });

  const justifyContent =
    resolvedPosition.x === 'center'
      ? 'center'
      : resolvedPosition.x === 'right'
        ? 'flex-end'
        : 'flex-start';
  const alignItems =
    resolvedPosition.y === '50%' || resolvedPosition.y === 'center' ? 'center' : 'flex-start';
  const paddingTop =
    typeof resolvedPosition.y === 'string' &&
    resolvedPosition.y !== '50%' &&
    resolvedPosition.y !== 'center'
      ? resolvedPosition.y
      : undefined;
  const paddingLeft = resolvedPosition.x === 'left' ? '8%' : undefined;
  const paddingRight = resolvedPosition.x === 'right' ? '8%' : undefined;

  return {
    isLandscape,
    position: resolvedPosition,
    maxWidth: isLandscape ? landscapeMaxWidth : portraitMaxWidth,
    containerStyle: {
      display: 'flex',
      justifyContent,
      alignItems,
      paddingTop,
      paddingLeft,
      paddingRight,
    },
  };
};
