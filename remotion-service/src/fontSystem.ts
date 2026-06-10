/**
 * 字体系统 — 分层字体加载与分发 (极速系统字体兜底版)
 *
 * 彻底移除了导致 Remotion 卡死的 @fontsource 800+ 本地文件并发请求。
 * 直接使用各平台的原生系统字体进行安全兜底，保证极速且不卡死。
 */

// 1. 移除了所有 @fontsource 导入，避免 Chromium 并发请求卡死
// 2. 将字体堆栈改为系统安全字体，确保跨平台秒出排版

const fonts = {
  title: '"STKaiti", "KaiTi", "BiauKai", serif', // 楷体/展示
  subtitle: '"PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif',
  accent: '"STXingkai", "Xingkai SC", "Brush Script MT", cursive',
  ui: '-apple-system, BlinkMacSystemFont, "Inter", "Segoe UI", sans-serif',
  number: '"Impact", "Arial Black", sans-serif',
  bodySerif: '"Songti SC", "SimSun", "Noto Serif SC", serif',
  bodySans: '"PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif',
  kaiStyle: '"STKaiti", "KaiTi", serif',
} as const;

/** 字体分层标识 */
export type FontTier = keyof typeof fonts;

/** 根据分层获取对应的 fontFamily 字符串 */
export const getFontFamily = (tier: FontTier): string => fonts[tier];

/** 默认字体分层 — 用于未指定层级的兜底场景 */
export const DEFAULT_FONT_TIER: FontTier = 'subtitle';
