/**
 * backing 底板共享工具 — 语义位置解析
 *
 * 协议与 media.image 的语义 position 保持一致：
 *   position: { x: "center" | "15%" | number, y: "center" | "25%" | number }
 *
 * 归一后输出 position: absolute + left/top/transform
 */
import type React from 'react';

type SemanticPosition = {
  x?: string | number;
  y?: string | number;
};

type ResolvedBackingPosition = {
  style: React.CSSProperties;
};

const appendTranslate = (current: string | undefined, segment: string): string => {
  const normalized = (current ?? '').trim();
  return normalized ? `${normalized} ${segment}` : segment;
};

export const resolveBackingPosition = (
  position?: SemanticPosition,
): ResolvedBackingPosition => {
  const style: React.CSSProperties = {
    position: 'absolute',
  };

  if (!position) {
    style.left = '50%';
    style.top = '50%';
    style.transform = 'translate(-50%, -50%)';
    return { style };
  }

  let transform: string | undefined;

  const { x, y } = position;

  if (x === 'center') {
    style.left = '50%';
    transform = appendTranslate(transform, 'translateX(-50%)');
  } else if (x != null) {
    style.left = x as React.CSSProperties['left'];
    if (typeof x === 'string' && x.trim().endsWith('%')) {
      transform = appendTranslate(transform, 'translateX(-50%)');
    }
  } else {
    style.left = '50%';
    transform = appendTranslate(transform, 'translateX(-50%)');
  }

  if (y === 'center') {
    style.top = '50%';
    transform = appendTranslate(transform, 'translateY(-50%)');
  } else if (y != null) {
    style.top = y as React.CSSProperties['top'];
    if (typeof y === 'string' && y.trim().endsWith('%')) {
      transform = appendTranslate(transform, 'translateY(-50%)');
    }
  } else {
    style.top = '50%';
    transform = appendTranslate(transform, 'translateY(-50%)');
  }

  if (transform) {
    style.transform = transform;
  }

  return { style };
};
