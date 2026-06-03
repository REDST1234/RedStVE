/**
 * 预设注册表 — 管理所有可用的视觉预设组件
 */
import React from 'react';

export type PresetId =
  | 'bg.mesh_gradient' | 'bg.tech_grid' | 'bg.noise_grain'
  | 'media.video' | 'media.image' | 'media.audio'
  | 'motion.ken_burns'
  | 'text.fade_title'
  | 'text.kinetic_pop'
  | 'text.typewriter'
  | 'text.mask_reveal'
  | 'text.word_highlight'
  | 'caption.subtitle'
  | 'transition.fade' | 'transition.slide' | 'transition.wipe'
  | 'overlay.light_leak'
  | 'overlay.flash'
  | 'overlay.badge_pop'
  | 'overlay.glow_frame';

export interface PresetEntry<TProps = unknown> {
  id: PresetId;
  component: React.ComponentType<TProps>;
}

const REGISTRY = new Map<string, PresetEntry<unknown>>();

/** 注册预设 */
export function registerPreset<TProps>(entry: PresetEntry<TProps>): void {
  REGISTRY.set(entry.id, entry as PresetEntry<unknown>);
}

/** 获取预设，若不存在则抛出错误 */
export function getPreset(id: string): PresetEntry<unknown> {
  const entry = REGISTRY.get(id);
  if (!entry) {
    throw new Error(`[PresetRegistry] 未知预设 ID: "${id}"，请检查 CompositionScript 中的 preset 字段。`);
  }
  return entry;
}

/** 列出所有已注册预设 */
export function listPresets(): string[] {
  return Array.from(REGISTRY.keys());
}
