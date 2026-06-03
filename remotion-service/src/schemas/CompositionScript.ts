/**
 * Composition Script 编排脚本协议 v1 — Zod Schema 定义
 * LLM 产出的视频编排 JSON 必须严格符合此协议
 */
import { z } from 'zod';

// ========== 基础类型 ==========

/** 二维坐标位置 */
export const PositionSchema = z.object({
  x: z.union([z.number(), z.string()]),
  y: z.union([z.number(), z.string()]),
});

/** CSS 样式子集 */
export const StyleSchema = z.object({
  width: z.union([z.number(), z.string()]).optional(),
  height: z.union([z.number(), z.string()]).optional(),
  objectFit: z.enum(['cover', 'contain', 'fill', 'none']).optional(),
  position: z.string().optional(),
  top: z.union([z.number(), z.string()]).optional(),
  left: z.union([z.number(), z.string()]).optional(),
}).passthrough().optional();

/** 贝塞尔缓动参数 [x1, y1, x2, y2] */
export const EasingSchema = z.tuple([z.number(), z.number(), z.number(), z.number()]);

// ========== 画布与全局样式 ==========

export const CanvasSchema = z.object({
  width: z.number().int().positive(),
  height: z.number().int().positive(),
  fps: z.number().int().positive().default(30),
});

export const GlobalStyleSchema = z.object({
  fontFamily: z.string().default('Noto Sans SC'),
  backgroundColor: z.string().default('#000000'),
});

// ========== BGM ==========

export const BgmSchema = z.object({
  src: z.string(),
  mixLevel: z.enum(['QUIET', 'BALANCED', 'DRIVE']).optional(),
  volume: z.number().min(0).max(1).default(0.3),
  loop: z.boolean().default(true),
  fadeInFrames: z.number().int().nonnegative().default(15),
  fadeOutFrames: z.number().int().nonnegative().default(30),
}).optional();

// ========== 图层 (Layer) ==========

export const LayerSchema = z.object({
  layerId: z.string(),
  preset: z.string(),
  enterAtFrame: z.number().int().nonnegative().optional(),
  durationInFrames: z.union([z.number().int().positive(), z.null()]).optional(),
  params: z.preprocess(
    (value) => (value == null ? {} : value),
    z.record(z.string(), z.any()),
  ),
});

// ========== 场景 (Scene) ==========

export const SceneSchema = z.object({
  sceneId: z.string(),
  sceneIndex: z.number().int().nonnegative(),
  role: z.union([z.string(), z.null()]).optional(),
  durationInFrames: z.number().int().positive(),
  layers: z.array(LayerSchema),
});

// ========== 转场 (Transition) ==========

export const TransitionOverlaySchema = z.object({
  preset: z.string(),
  params: z.preprocess(
    (value) => (value == null ? {} : value),
    z.record(z.string(), z.any()),
  ),
}).optional();

export const TransitionSchema = z.object({
  fromSceneIndex: z.number().int().nonnegative(),
  toSceneIndex: z.number().int().nonnegative(),
  preset: z.string(),
  params: z.preprocess(
    (value) => (value == null ? {} : value),
    z.object({
      direction: z.string().optional(),
      durationInFrames: z.number().int().positive().default(15),
      timing: z.enum(['linear', 'spring']).default('linear'),
    }).passthrough(),
  ),
  overlay: z.union([TransitionOverlaySchema, z.null()]).optional(),
});

// ========== 顶层编排脚本 ==========

export const CompositionScriptSchema = z.object({
  $schema: z.string().optional(),
  projectId: z.string().optional(),
  canvas: CanvasSchema,
  globalStyle: z.union([GlobalStyleSchema, z.null()]).optional(),
  bgm: BgmSchema,
  scenes: z.array(SceneSchema).min(1),
  transitions: z.array(TransitionSchema).default([]),
});

// ========== 导出类型 ==========

export type Position = z.infer<typeof PositionSchema>;
export type CompositionScript = z.infer<typeof CompositionScriptSchema>;
export type Scene = z.infer<typeof SceneSchema>;
export type Layer = z.infer<typeof LayerSchema>;
export type Transition = z.infer<typeof TransitionSchema>;
