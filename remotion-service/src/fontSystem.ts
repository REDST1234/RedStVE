/**
 * 字体系统 — 分层字体加载与分发
 *
 * 设计目标：
 * 1. 标题 (Title) → 艺术感强的展示字体，如 ZCOOL XiaoWei
 * 2. 字幕 (Subtitle) → 高可读性无衬线体，如 Noto Sans SC
 * 3. 强调 (Accent) → 手写/书法风格制造视觉反差，如 Ma Shan Zheng
 * 4. UI/标签 → 现代干净的无衬线体，如 Inter + Noto Sans SC 混合
 * 5. 数字 (Number) → 粗体展示字体，如 Bebas Neue
 * 6. 正文衬线 (BodySerif) → 优雅衬线体，如 Noto Serif SC
 * 7. 楷体风格 (KaiStyle) → 文学/引语场景，如 LXGW WenKai TC
 * 8. 正文无衬线 (BodySans) → 保底通用字体，Noto Sans SC
 *
 * 所有字体通过 @remotion/google-fonts 按需加载，
 * Remotion 在渲染时自动从 Google Fonts 下载并注册，不增加视频体积。
 */
import { loadFont as loadNotoSansSC } from '@remotion/google-fonts/NotoSansSC';
import { loadFont as loadNotoSerifSC } from '@remotion/google-fonts/NotoSerifSC';
import { loadFont as loadZCOOLXiaoWei } from '@remotion/google-fonts/ZCOOLXiaoWei';
import { loadFont as loadMaShanZheng } from '@remotion/google-fonts/MaShanZheng';
import { loadFont as loadLXGWWenKaiTC } from '@remotion/google-fonts/LXGWWenKaiTC';
import { loadFont as loadInter } from '@remotion/google-fonts/Inter';
import { loadFont as loadBebasNeue } from '@remotion/google-fonts/BebasNeue';

const fonts = {
  title: loadZCOOLXiaoWei(),
  subtitle: loadNotoSansSC(),
  accent: loadMaShanZheng(),
  ui: loadInter(),
  number: loadBebasNeue(),
  bodySerif: loadNotoSerifSC(),
  bodySans: loadNotoSansSC(),
  kaiStyle: loadLXGWWenKaiTC(),
} as const;

/** 字体分层标识 */
export type FontTier = keyof typeof fonts;

/** 根据分层获取对应的 fontFamily 字符串 */
export const getFontFamily = (tier: FontTier): string => fonts[tier].fontFamily;

/** 默认字体分层 — 用于未指定层级的兜底场景 */
export const DEFAULT_FONT_TIER: FontTier = 'subtitle';
