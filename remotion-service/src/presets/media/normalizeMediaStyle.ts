import type React from 'react';

type SemanticPosition = {
  x?: string | number;
  y?: string | number;
};

const isSemanticPosition = (value: unknown): value is SemanticPosition => {
  return typeof value === 'object' && value !== null && ('x' in value || 'y' in value);
};

const appendTranslate = (current: string | undefined, segment: string): string => {
  const normalized = (current ?? '').trim();
  return normalized ? `${normalized} ${segment}` : segment;
};

export const normalizeMediaStyle = (
  style: React.CSSProperties | Record<string, unknown> | undefined,
  defaultObjectFit: 'cover' | 'contain' = 'cover',
): React.CSSProperties => {
  const nextStyle: React.CSSProperties = {
    width: '100%',
    height: '100%',
    objectFit: defaultObjectFit,
  };

  if (!style) {
    return nextStyle;
  }

  const rawStyle = { ...style } as Record<string, unknown>;
  const hasExplicitObjectFit = Object.prototype.hasOwnProperty.call(rawStyle, 'objectFit');
  const hasExplicitWidth = Object.prototype.hasOwnProperty.call(rawStyle, 'width');
  const hasExplicitHeight = Object.prototype.hasOwnProperty.call(rawStyle, 'height');
  const maybePosition = rawStyle.position;
  const semanticX = rawStyle.x;
  const semanticY = rawStyle.y;
  delete rawStyle.position;
  delete rawStyle.x;
  delete rawStyle.y;

  Object.assign(nextStyle, rawStyle);

  const normalizedSemanticPosition =
    isSemanticPosition(maybePosition)
      ? maybePosition
      : semanticX != null || semanticY != null
        ? { x: semanticX as string | number | undefined, y: semanticY as string | number | undefined }
        : null;

  if (!normalizedSemanticPosition) {
    if (maybePosition != null) {
      nextStyle.position = maybePosition as React.CSSProperties['position'];
    }
    return nextStyle;
  }

  nextStyle.position = 'absolute';
  if (!hasExplicitObjectFit) {
    nextStyle.objectFit = 'contain';
  }

  // 对被语义定位的图片/视频，不再保留默认的 height=100% 整屏盒子，
  // 否则在设置 top/y 后会把整块媒体向下压出画面。
  if (!hasExplicitWidth && !hasExplicitHeight) {
    nextStyle.width = 'auto';
    nextStyle.height = 'auto';
    nextStyle.maxWidth = '100%';
    nextStyle.maxHeight = '100%';
  } else if (hasExplicitWidth && !hasExplicitHeight) {
    nextStyle.height = 'auto';
    nextStyle.maxHeight = '100%';
  } else if (!hasExplicitWidth && hasExplicitHeight) {
    nextStyle.width = 'auto';
    nextStyle.maxWidth = '100%';
  }

  let transform = typeof nextStyle.transform === 'string' ? nextStyle.transform : undefined;
  const { x, y } = normalizedSemanticPosition;

  if (x === 'center') {
    nextStyle.left = '50%';
    transform = appendTranslate(transform, 'translateX(-50%)');
  } else if (x != null) {
    nextStyle.left = x as React.CSSProperties['left'];
  }

  if (y === 'center') {
    nextStyle.top = '50%';
    transform = appendTranslate(transform, 'translateY(-50%)');
  } else if (y != null) {
    nextStyle.top = y as React.CSSProperties['top'];
    if (typeof y === 'string' && y.trim().endsWith('%')) {
      transform = appendTranslate(transform, 'translateY(-50%)');
    }
  }

  if (transform) {
    nextStyle.transform = transform;
  }

  return nextStyle;
};
