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
  wrapperStyle: React.CSSProperties;
  textAlign: 'left' | 'center' | 'right';
  contentJustify: 'flex-start' | 'center' | 'flex-end';
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
      return { x: isLandscape ? '18%' : '16%', y: isLandscape ? '40%' : '34%' };
    case 'right_focus':
      return { x: isLandscape ? '82%' : '84%', y: isLandscape ? '40%' : '34%' };
    default:
      return { x: 'center', y: isLandscape ? '48%' : '42%' };
  }
};

const appendTranslate = (current: string | undefined, segment: string): string => {
  const normalized = (current ?? '').trim();
  return normalized ? `${normalized} ${segment}` : segment;
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

  const wrapperStyle: React.CSSProperties = {
    position: 'absolute',
    maxWidth: isLandscape ? landscapeMaxWidth : portraitMaxWidth,
  };
  let transform = typeof wrapperStyle.transform === 'string' ? wrapperStyle.transform : undefined;

  const x = resolvedPosition.x;
  const y = resolvedPosition.y;

  if (x === 'center') {
    wrapperStyle.left = '50%';
    transform = appendTranslate(transform, 'translateX(-50%)');
  } else if (x === 'left') {
    wrapperStyle.left = '8%';
  } else if (x === 'right') {
    wrapperStyle.right = '8%';
  } else {
    wrapperStyle.left = x as React.CSSProperties['left'];
    transform = appendTranslate(transform, 'translateX(-50%)');
  }

  if (y === 'center') {
    wrapperStyle.top = '50%';
    transform = appendTranslate(transform, 'translateY(-50%)');
  } else if (y != null) {
    wrapperStyle.top = y as React.CSSProperties['top'];
    transform = appendTranslate(transform, 'translateY(-50%)');
  }

  if (transform) {
    wrapperStyle.transform = transform;
  }

  const textAlign =
    positionPreset === 'right_focus' || resolvedPosition.x === 'right'
      ? 'right'
      : positionPreset === 'left_focus' || resolvedPosition.x === 'left'
        ? 'left'
        : 'center';
  const contentJustify =
    textAlign === 'right' ? 'flex-end' : textAlign === 'left' ? 'flex-start' : 'center';

  return {
    isLandscape,
    position: resolvedPosition,
    maxWidth: isLandscape ? landscapeMaxWidth : portraitMaxWidth,
    wrapperStyle,
    textAlign,
    contentJustify,
  };
};
