# 批次 5：Remotion 预设库与 LLM 编排渲染模块

## 5.1 模块职责

本模块是创作链路的终端产出层。它将前序链路产出的**素材档案 + 已匹配模板 + 已推荐 BGM**，通过 LLM 编排引擎生成标准化的 **Composition Script（编排脚本 JSON）**，再由 Remotion 渲染微服务将其映射到预设组件库，最终输出成品视频。

核心职责：
- 维护一套**参数化的 Remotion 预设组件库**，覆盖短视频制作的刚需视觉手法
- 定义 **LLM ↔ 预设库的 JSON 通信协议**（Composition Script Protocol）
- 提供 **Node.js 渲染微服务**，接收编排 JSON 并驱动 Remotion SSR 输出 MP4
- 在 Spring Boot 后端中编排 LLM 调用与渲染任务的异步流程

## 5.2 技术可行性验证结论

| 验证项 | 结论 | 依据 |
|--------|------|------|
| JSON 驱动渲染 | ✅ 原生支持 | Remotion `inputProps` + `calculateMetadata` 机制 |
| Node.js 程序化渲染 | ✅ 原生支持 | `@remotion/renderer` 的 `renderMedia()` API |
| CLI 渲染 | ✅ 原生支持 | `npx remotion render <id> out.mp4 --props=./script.json` |
| 参数化组件 | ✅ 原生支持 | Zod Schema + React props |
| 动态时长/尺寸 | ✅ 原生支持 | `calculateMetadata` 根据 props 动态计算 |

## 5.3 全景流程

```
素材档案 (PROFILED) + 模板 JSON + BGM 推荐结果
                    │
                    ▼
         Spring Boot：聚合上下文 → 调用 LLM (Ark)
                    │
                    ▼
         LLM 输出 Composition Script JSON
                    │
                    ▼
         后端校验 JSON → 投递异步渲染任务
                    │
                    ▼
         Remotion 渲染微服务 (Node.js)
         ├── 解析 JSON → 映射 PresetRegistry
         ├── 组装 React 组件树 (TransitionSeries)
         └── renderMedia() → 输出 MP4
                    │
                    ▼
         回传产物路径 → 前端播放/下载
```

## 5.4 预设库设计——仅保留做视频的刚需手法

### 5.4.1 预设清单（首期落地范围）

| 预设 ID | 类别 | 用途 | 核心参数 |
|---------|------|------|---------|
| `bg.mesh_gradient` | 背景 | 纯代码流体渐变背景 | `colors, intensity, blendMode, layoutMode` |
| `bg.tech_grid` | 背景 | 纯代码科技网格背景 | `backgroundColor, lineColor, accentColor, gridSize, lineOpacity, driftSpeed, layoutMode` |
| `bg.noise_grain` | 背景 | 纯代码噪点/磨砂背景 | `backgroundColor, grainOpacity, scale, layoutMode` |
| `media.video` | 媒体 | 嵌入视频片段 | `src, trimBefore, trimAfter, volume, playbackRate, style` |
| `media.image` | 媒体 | 嵌入静态图片 | `src, style(objectFit, width, height)` |
| `media.audio` | 媒体 | BGM / 音效叠加 | `src, volume, loop, fadeInFrames, fadeOutFrames` |
| `motion.ken_burns` | 运镜 | 图片伪运镜（推/拉/平移） | `src, startScale, endScale, startPos, endPos, easing` |
| `text.fade_title` | 文字 | 标题卡淡入淡出 | `text, fontSize, color, fontWeight, position, positionPreset, layoutMode, easing` |
| `text.kinetic_pop` | 文字 | 冲击型弹簧大字报 | `text, fontSize, color, fontWeight, position, positionPreset, layoutMode, scaleFrom, scaleTo, rotationFrom, rotationTo, enterFrames, settleFrames` |
| `text.typewriter` | 文字 | 打字机短标题 | `text, fontSize, color, fontWeight, position, positionPreset, layoutMode, charIntervalFrames, cursor, cursorColor, cursorScale` |
| `text.mask_reveal` | 文字 | 遮罩滑入文字 | `text, fontSize, color, fontWeight, position, positionPreset, layoutMode, revealDirection, revealFrames` |
| `text.word_highlight` | 文字 | 逐词高亮短句 | `text/tokens, highlightWords, fontSize, color, highlightColor, highlightBackground, position, positionPreset, layoutMode, wordDurationInFrames` |
| `caption.subtitle` | 字幕 | 底部字幕条 | `text, fontSize, color, bgColor, position` |
| `transition.fade` | 转场 | 淡入淡出转场 | `durationInFrames` |
| `transition.slide` | 转场 | 滑动转场 | `direction, durationInFrames` |
| `transition.wipe` | 转场 | 擦除转场 | `durationInFrames` |
| `overlay.light_leak` | 叠加 | 光效叠加 | `seed, hueShift, durationInFrames` |
| `overlay.flash` | 叠加 | 短时闪光冲击层 | `color, maxOpacity, enterFrames, holdFrames, exitFrames` |
| `overlay.badge_pop` | 叠加 | 卖点/标签角标弹出 | `text, bgColor, color, position, scaleFrom, scaleTo, rotation` |
| `overlay.glow_frame` | 叠加 | 主体高亮边框层 | `color, thickness, glowBlur, opacity, borderRadius, inset` |

> [!IMPORTANT]
> 首期**不包含**：图表动效、3D 渲染、GIF、Lottie、地图、AI 配音、音频可视化。这些归入后续迭代。

### 5.4.2 各类能力接入协议（当前统一口径）

为了避免“看起来是一个特效，实际上协议边界完全不同”的混乱，当前 Remotion 能力接入统一分为 5 类。后续新增能力必须先归类，再按对应协议接入。

#### A. 纯代码型视觉特效（推荐优先接入）

特征：

- 不依赖外部素材资源
- 直接使用 React + Remotion 插值能力实现
- 最适合快速扩展包装能力

典型能力：

- 文字动效：`text.kinetic_pop`、`text.typewriter`、`text.mask_reveal`、`text.word_highlight`
- 叠加包装：`overlay.flash`、`overlay.badge_pop`、`overlay.glow_frame`
- 背景库：`bg.mesh_gradient`、`bg.tech_grid`、`bg.noise_grain`
- 现有已接入：`text.fade_title`、`overlay.light_leak`

接入协议：

1. 在 `remotion-service/src/presets/...` 下新增组件文件
2. 在 `PresetRegistry` 中新增 `PresetId`
3. 在 `registerAll.ts` 注册 preset
4. 在 `REMOTION_ORCHESTRATOR_JSON` 中补充允许列表、参数说明、使用场景
5. `CompositionScript` 不需要新增强类型字段，继续通过 `layer.params` 透传

资源要求：

- 无外部资源依赖
- 所有效果必须可仅凭 `params` 渲染
- 若组件涉及明显构图差异（如背景、网格、空间透视），建议支持 `layoutMode: auto|portrait|landscape`，默认根据 `canvas` 自动适配横竖屏

#### B. 资源型视觉特效

特征：

- 效果本身依赖外部资源文件
- 没有资源时，仅接入播放器没有意义

典型能力：

- Lottie 动画
- 透明视频贴层
- GIF 贴层

推荐接入顺序：

1. `overlay.lottie`
2. 透明视频 overlay
3. GIF 贴层（优先级低于透明视频）

接入协议：

1. 先确认播放器/运行时依赖可用
2. 再确认资源文件可被 `remotion-service` 以 HTTP URL 访问
3. 为资源型能力定义独立 preset（如 `overlay.lottie`）
4. 在 Prompt 中明确：
   - 只能使用系统提供的资源 URL
   - 没有可用资源时不得虚构使用
5. 若资源有固定用途，建议在后端给 LLM 注入“包装资源说明”

资源要求：

- 必须有可访问的资源文件
- 资源建议统一托管到 `/storage/...`
- 若资源内部依赖外链图片，必须一并托管

#### C. 音频/音效型能力

特征：

- 作用在声轨，不是画面层
- 一部分走顶层 `bgm`，一部分走局部 `media.audio`

当前口径：

- 顶层 `bgm`：全局背景音乐主通道
- `media.audio`：局部音效、片头片尾音、特殊提示音、未来 SFX 通道

典型能力：

- `sfx_hit`
- `sfx_whoosh`
- `sfx_riser`
- 局部环境补声

接入协议：

1. 若是全局背景音乐，必须走顶层 `bgm`
2. 若是局部音效，必须走 `media.audio`
3. 若要新增固定 SFX 语义，优先通过资源说明约束，而不是先扩大量新 preset
4. 在 Prompt 中必须写清：
   - 何时允许使用局部音效
   - 不得把全局 BGM 伪装成 `media.audio`

资源要求：

- 必须有已知 URL 的音频资源
- 需与现有 BGM / SFX 资源库管理策略一致

#### D. 字幕与文本理解型能力

特征：

- 不只是“显示一段文字”，还可能涉及逐词高亮、SRT、口播对齐、关键词强调

当前已接入：

- `caption.subtitle`
- `text.fade_title`

后续可接：

- `text.typewriter`
- `text.word_highlight`
- 高级字幕（逐词高亮、SRT 对齐）

接入协议：

1. 若只需要视觉包装效果，可做成普通 text/caption preset
2. 若需要和语音时间轴对齐，则要额外定义字幕数据来源与时间粒度
3. Prompt 中必须区分：
   - 标题卡
   - 底部字幕
   - 强调词动画

资源要求：

- 纯文本动效无外部资源要求
- 若使用 SRT / 逐词字幕，需额外有时间轴文本数据

#### E. 重型能力（后续扩展类）

特征：

- 接入成本高
- 通常会牵动更多底层协议、资源体系或渲染性能

典型能力：

- 3D
- 地图
- 音频可视化
- HTML in Canvas

接入协议：

1. 必须先完成小规模 PoC
2. 明确性能预算与渲染耗时影响
3. 明确是否需要新增专门 schema、素材类型或资源托管策略
4. 不建议在首轮直接开放给 LLM 自由调用

#### 当前系统已接入/待接入状态矩阵

| 能力类别 | 当前状态 | 说明 |
|---------|----------|------|
| 纯代码型视觉特效 | 已接入增强版 | 已有 `text.fade_title`、`text.kinetic_pop`、`text.typewriter`、`text.mask_reveal`、`text.word_highlight`、`overlay.light_leak`、`overlay.flash`、`overlay.badge_pop`、`overlay.glow_frame`，并新增 `bg.mesh_gradient`、`bg.tech_grid`、`bg.noise_grain` 背景库 |
| 资源型视觉特效 | 未接入 | Lottie / 透明视频 / GIF 目前还未接系统主链 |
| 音频/音效型能力 | 部分接入 | 顶层 `bgm` 与 `media.audio` 已通，SFX 资源语义尚未产品化 |
| 字幕与文本理解型能力 | 部分接入 | 已有基础标题与字幕，缺逐词高亮和高级字幕体系 |
| 重型能力 | 未接入 | 3D / 地图 / 音频可视化暂未纳入当前协议主链 |

#### 当前推荐的扩展优先级

第一梯队：

1. 纯代码型文字动效
2. 纯代码型包装 overlay
3. 局部 SFX 资源接入

第二梯队：

1. `overlay.lottie`
2. 高级字幕
3. 透明视频贴层

第三梯队：

1. 音频可视化
2. 地图
3. 3D

#### 第一梯队具体接入清单（建议按此顺序落地）

本节给出“可以直接进入实现”的接入清单。默认目标是：

- 不新增外部资源依赖
- 不改动顶层 `CompositionScript` 协议结构
- 通过新增 preset + Prompt 允许面扩展能力

##### 1. `text.kinetic_pop`

定位：

- 适用于 hook 开场大字报
- 适用于数字/卖点爆点
- 适用于 CTA 前的强记忆点

预设 ID：

- `text.kinetic_pop`

建议参数：

- `text`
- `fontSize`
- `color`
- `fontWeight`
- `position`
- `scaleFrom`
- `scaleTo`
- `rotationFrom`
- `rotationTo`
- `enterFrames`
- `settleFrames`
- `textShadow`

代码接入点：

1. 组件文件：
   - `remotion-service/src/presets/text/KineticPopText.tsx`
2. `PresetRegistry` 增加 `text.kinetic_pop`
3. `registerAll.ts` 注册新 preset
4. `REMOTION_ORCHESTRATOR_JSON` 增加该预设说明与使用边界

LLM 使用规则：

- 只用于短句、数字、核心词冲击
- 不应用于长段落说明
- 通常放在 hook 或高潮节点

验收标准：

- 文字能产生明显的 pop-in 冲击感
- 结束后快速稳定落位
- 不会因为过大缩放导致首帧难以辨认

##### 2. `text.typewriter`

定位：

- 适用于 hook 标题
- 适用于教程步骤标题
- 适用于 CTA 前的短句引导

预设 ID：

- `text.typewriter`

建议参数：

- `text`
- `fontSize`
- `color`
- `fontWeight`
- `position`
- `charIntervalFrames`
- `cursor`
- `textShadow`

代码接入点：

1. 新增组件文件：
   - `remotion-service/src/presets/text/TypewriterTitle.tsx`
2. 在 `PresetRegistry` 增加 `text.typewriter`
3. 在 `registerAll.ts` 注册新 preset
4. 在 `REMOTION_ORCHESTRATOR_JSON` 增加该预设说明、参数约束和适用场景

LLM 使用规则：

- 仅用于短标题或强调句
- 不应用于长段落字幕
- 单层文本长度建议控制在 8~20 个汉字或等价英文长度

验收标准：

- 能按字符逐步显示文本
- 结束后稳定停留，不闪烁
- 与现有 `text.fade_title` 可并存，不互相替代

##### 3. `text.mask_reveal`

定位：

- 适用于功能说明
- 适用于科技感标题
- 适用于更克制、更高级的中段文案

预设 ID：

- `text.mask_reveal`

建议参数：

- `text`
- `fontSize`
- `color`
- `fontWeight`
- `position`
- `revealDirection`
- `revealFrames`
- `textShadow`

代码接入点：

1. 组件文件：
   - `remotion-service/src/presets/text/MaskRevealText.tsx`
2. `PresetRegistry` 增加 `text.mask_reveal`
3. `registerAll.ts` 注册新 preset
4. `REMOTION_ORCHESTRATOR_JSON` 中补充其说明

LLM 使用规则：

- 适合整句短文案，不适合超长字幕
- 优先用于功能说明、科技型表达或 CTA 前的克制强调
- 需要明确 reveal 方向，不要同时叠加多个复杂方向

验收标准：

- 文字能通过遮罩顺滑显现
- reveal 完成后稳定停留
- 视觉效果明显区别于 `text.typewriter`

##### 4. `text.word_highlight`

定位：

- 适用于卖点关键词高亮
- 适用于教程步骤中的重点词
- 适用于 CTA 中的关键动作词强调

预设 ID：

- `text.word_highlight`

建议参数：

- `text`
- `highlightWords`
- `fontSize`
- `color`
- `highlightColor`
- `fontWeight`
- `position`
- `textShadow`

代码接入点：

1. 新增组件文件：
   - `remotion-service/src/presets/text/WordHighlightText.tsx`
2. 在 `PresetRegistry` 增加 `text.word_highlight`
3. 在 `registerAll.ts` 注册新 preset
4. 在 `REMOTION_ORCHESTRATOR_JSON` 中补充其说明

LLM 使用规则：

- 仅在短句中高亮 1~3 个关键词
- 不允许把整句全部标成高亮
- 若没有明确关键词，则优先回退 `text.fade_title`

验收标准：

- 关键词颜色或亮度变化明显
- 非高亮文本保持稳定可读
- 画面中不会因为过多高亮导致主信息失焦

##### 3. `overlay.flash`

定位：

- 适用于高潮、转折、击打点、价格强调
- 作为短时冲击型 overlay，不承担持续信息表达

预设 ID：

- `overlay.flash`

建议参数：

- `color`
- `maxOpacity`
- `enterFrames`
- `holdFrames`
- `exitFrames`

代码接入点：

1. 新增组件文件：
   - `remotion-service/src/presets/overlay/FlashOverlay.tsx`
2. 在 `PresetRegistry` 增加 `overlay.flash`
3. 在 `registerAll.ts` 注册新 preset
4. 在 Prompt 中说明它适用于“短时冲击”，不应全片滥用

LLM 使用规则：

- 单次使用时长宜短
- 只用于关键强调点
- 不建议每个 scene 都出现

验收标准：

- 可叠加在 scene 层或 transition overlay 上
- 不遮挡主体超过可接受时长
- 短时冲击感明确

##### 4. `overlay.badge_pop`

定位：

- 适用于卖点标签
- 适用于“新品 / 限时 / 免费 / 推荐”等包装角标
- 对“包装补全”特别有价值

预设 ID：

- `overlay.badge_pop`

建议参数：

- `text`
- `bgColor`
- `color`
- `position`
- `scaleFrom`
- `scaleTo`
- `rotation`
- `padding`

代码接入点：

1. 新增组件文件：
   - `remotion-service/src/presets/overlay/BadgePopOverlay.tsx`
2. 在 `PresetRegistry` 增加 `overlay.badge_pop`
3. 在 `registerAll.ts` 注册新 preset
4. 在 Prompt 中增加“包装补全优先使用场景”

LLM 使用规则：

- 只承载短文字
- 优先用于 hook、卖点段、CTA 段
- 如果已有大量字幕，不要再叠过多 badge

验收标准：

- 有明确 pop-in 动效
- 角标视觉层级高于背景、低于主标题
- 小屏预览下仍可读

##### 5. `overlay.glow_frame`

定位：

- 适用于主体高亮
- 适用于“重点展示 / 推荐片段 / 高光卡段”边框增强

预设 ID：

- `overlay.glow_frame`

建议参数：

- `color`
- `thickness`
- `glowBlur`
- `opacity`
- `borderRadius`

代码接入点：

1. 新增组件文件：
   - `remotion-service/src/presets/overlay/GlowFrameOverlay.tsx`
2. 在 `PresetRegistry` 增加 `overlay.glow_frame`
3. 在 `registerAll.ts` 注册新 preset
4. 在 Prompt 中说明适用于“主体强调/高光标记”

LLM 使用规则：

- 不应用于全片持续包边
- 更适合短时间强调某一 scene 或某个重点时刻

验收标准：

- 边框与发光层可稳定渲染
- 不因 glow 过强污染画面主体

##### 6. 局部 SFX 接入（先不强制新增 preset）

定位：

- 适用于 whoosh / hit / riser / click 等局部包装音效

建议接法：

- 第一轮继续复用 `media.audio`
- 不急着新增 `audio.sfx_*` 预设
- 通过“音效资源池 + Prompt 使用约束”先跑通

资源要求：

- 建立一批可访问的音效 URL
- 每个音效附带用途说明：
  - `hit`
  - `whoosh`
  - `riser`
  - `click`

代码接入点：

1. 后端为编排模型补充“可用音效资源说明”
2. `REMOTION_ORCHESTRATOR_JSON` 中补充：
   - `media.audio` 可用于局部 SFX
   - 禁止把它当全局 BGM 主通道

LLM 使用规则：

- 仅在关键节点使用
- 单 scene 内局部音效数量要受控
- 若已经有强 BGM 节奏驱动，则减少 SFX 密度

验收标准：

- 音效能和 `bgm` 并存
- 不会破坏已有全局 BGM 逻辑
- 音效使用在语义上可解释

##### 第一梯队统一实施顺序

建议严格按以下顺序落地：

1. `text.typewriter`
2. `text.word_highlight`
3. `overlay.flash`
4. `overlay.badge_pop`
5. `overlay.glow_frame`
6. `media.audio` 语义下的局部 SFX 资源接入

> 当前实现已进一步前置落地：
> `text.kinetic_pop` 与 `text.mask_reveal` 已作为纯文本 MG 的基础文字武器库接入系统。

理由：

- 文字动效与纯代码 overlay 不依赖外部资源
- 对现有系统侵入最小
- 可以最快提升“包装补全”和“成片质感”
- SFX 最后接，避免音频策略与 BGM 主通道过早耦合

### 5.4.3 预设组件的工程约束

1. **禁止 CSS transitions / animations** —— 所有动效必须使用 `interpolate()` + `Easing.bezier()`
2. **禁止 Tailwind 动画类名** —— 不会在服务端渲染中生效
3. **资源引用** —— 通过 `staticFile()` 引用 `public/` 目录，或直接使用远程 URL
4. **字体** —— 使用 `@remotion/google-fonts` 加载 Noto Sans SC（中文）和 Inter（英文）

### 5.4.4 预设注册表结构

```typescript
// src/presets/PresetRegistry.ts

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

interface PresetEntry {
  id: PresetId;
  component: React.FC<any>;         // 渲染组件
  schema: z.ZodObject<any>;         // 参数校验
}

const REGISTRY = new Map<PresetId, PresetEntry>();

export function registerPreset(entry: PresetEntry): void;
export function getPreset(id: PresetId): PresetEntry;
export function listPresets(): PresetId[];
```

## 5.5 Composition Script 通信协议（v1）

### 5.5.1 顶层结构

```jsonc
{
  "$schema": "composition-script/v1",
  "projectId": "crp_xxx",

  "canvas": {
    "width": 1080,
    "height": 1920,
    "fps": 30
  },

  "globalStyle": {
    "fontFamily": "Noto Sans SC",
    "backgroundColor": "#000000"
  },

  "bgm": {
    "src": "/api/storage/audio-database/xxx/track.mp3",
    "volume": 0.3,
    "loop": true,
    "fadeInFrames": 15,
    "fadeOutFrames": 30
  },

  "scenes": [ /* Scene 对象数组 */ ],
  "transitions": [ /* 场景间转场定义 */ ]
}
```

### 5.5.2 Scene（场景）协议

每个 Scene 对应模板中的一个 Segment，包含多个按 z-index 排列的图层。

```jsonc
{
  "sceneId": "scene_0",
  "sceneIndex": 0,
  "role": "hook",
  "durationInFrames": 90,

  "layers": [
    {
      "layerId": "bg_video_0",
      "preset": "media.video",
      "params": {
        "src": "/api/storage/creation/proj_xxx/raw_001.mp4",
        "trimBefore": 135,
        "trimAfter": 300,
        "volume": 0,
        "style": { "width": "100%", "height": "100%", "objectFit": "cover" }
      }
    },
    {
      "layerId": "title_0",
      "preset": "text.fade_title",
      "enterAtFrame": 10,
      "durationInFrames": 60,
      "params": {
        "text": "这杯咖啡绝了！",
        "fontSize": 52,
        "color": "#FFFFFF",
        "fontWeight": 700,
        "position": { "x": "center", "y": "75%" },
        "easing": [0.16, 1, 0.3, 1]
      }
    },
    {
      "layerId": "sub_0",
      "preset": "caption.subtitle",
      "enterAtFrame": 0,
      "params": {
        "text": "第一口就爱上了",
        "fontSize": 28,
        "color": "#FFFFFF",
        "bgColor": "rgba(0,0,0,0.6)",
        "position": "bottom_center"
      }
    }
  ]
}
```

### 5.5.3 Transition（转场）协议

```jsonc
{
  "fromSceneIndex": 0,
  "toSceneIndex": 1,
  "preset": "transition.slide",
  "params": {
    "direction": "from-left",
    "durationInFrames": 15,
    "timing": "linear"
  },
  "overlay": {
    "preset": "overlay.light_leak",
    "params": { "seed": 3, "hueShift": 120, "durationInFrames": 20 }
  }
}
```

### 5.5.4 运镜场景示例（图片素材 → Ken Burns）

当素材为图片时，LLM 应选择 `motion.ken_burns` 预设：

```jsonc
{
  "layerId": "bg_img_0",
  "preset": "motion.ken_burns",
  "params": {
    "src": "/api/storage/creation/proj_xxx/img_001.jpg",
    "startScale": 1.0,
    "endScale": 1.25,
    "startPosition": { "x": 0, "y": 0 },
    "endPosition": { "x": -30, "y": -20 },
    "easing": [0.45, 0, 0.55, 1]
  }
}
```

### 5.5.5 协议与当前模板 JSON 的字段映射

| 模板 v2 字段 | 编排 JSON 映射 |
|-------------|---------------|
| `meta.resolution` | → `canvas.width / height` |
| `meta.aspectRatio` | → 计算 `canvas.width / height` |
| `scriptStructure.segments[].role` | → `scene.role` |
| `scriptStructure.segments[].durationRange` | → LLM 决定 `scene.durationInFrames` |
| `scriptStructure.segments[].preferredShotTypes` | → 优先决定使用 `media.video`、`media.image` 或 `motion.ken_burns` 的段落倾向 |
| `scriptStructure.segments[].preferredCameraMovements` | → 优先决定运镜预设参数与镜头推进方式 |
| `scriptStructure.segments[].requiredVisualFunctions` | → 约束该段应优先选用哪类素材功能（如 `usage_process`、`cta_prompt`） |
| `scriptStructure.segments[].subtitleStrategy` / `packagingDensity` | → 影响文字层密度与包装层使用强度 |
| `shots[]` | → 仅作为代表性镜头原型补充，用于兼容旧模板与提供额外镜头解释 |
| `packagingStructure.subtitleStyle` | → `caption.subtitle` 的 params |
| `packagingStructure.transitions` | → `transitions[]` 数组 |
| `rhythmStructure.beatSyncPoints` | → 转场/特效的时间锚点 |

> [!NOTE]
> 当前创作链路已经从“`shots[]` 主导编排理解”收敛为“`segments[]` 抽象字段主导，`shots[]` 兼容补充”。这与模板推荐、槽位匹配、模板向量索引的消费口径保持一致。

## 5.6 系统架构

```
┌───────────────────────────────────────────────────────────┐
│                    前端 (React/Vite)                       │
│  "生成视频" 按钮 → 轮询状态 → 预览/下载成片                │
└──────────┬──────────────────────────────────┬──────────────┘
           │ POST /{projectId}/render-video   │ GET /render-status
           ▼                                  │
┌──────────────────────────────────────────────┤─────────────┐
│              Spring Boot 后端                │             │
│                                              │             │
│  RenderOrchestrationService:                 │             │
│  1. 聚合：素材 Profile + 模板 + BGM           │             │
│  2. 调用 Ark LLM → 产出 Composition Script   │             │
│  3. 校验 JSON 合法性                          │             │
│  4. POST 到 Remotion 渲染服务                 │             │
│  5. 异步轮询/回调更新渲染状态                  ◄─────────────┘
└──────────┬────────────────────────────────────────────────┘
           │ HTTP POST /render  { compositionScript }
           ▼
┌───────────────────────────────────────────────────────────┐
│           Remotion 渲染微服务 (Node.js + Express)          │
│                                                           │
│  remotion-preset-lib/                                     │
│  ├── src/                                                 │
│  │   ├── presets/                                         │
│  │   │   ├── media/     VideoClip, ImageLayer, AudioTrack │
│  │   │   ├── motion/    KenBurns                          │
│  │   │   ├── text/      FadeTitle                         │
│  │   │   ├── caption/   Subtitle                          │
│  │   │   ├── transition/ (fade, slide, wipe 映射函数)     │
│  │   │   └── overlay/   LightLeakWrapper                  │
│  │   ├── PresetRegistry.ts                                │
│  │   ├── DynamicVideoRenderer.tsx  (主渲染器)             │
│  │   ├── SceneRenderer.tsx         (场景渲染器)           │
│  │   └── Root.tsx                  (Composition 注册)     │
│  ├── server.ts           (Express HTTP 入口)              │
│  └── package.json                                         │
│                                                           │
│  流程：                                                    │
│  接收 JSON → 写入 public/ 目录（素材链接）                 │
│  → bundle() → selectComposition() → renderMedia()         │
│  → 回传 MP4 路径                                          │
└───────────────────────────────────────────────────────────┘
```

## 5.7 核心组件实现规约

### 5.7.1 DynamicVideoRenderer（主渲染器）

职责：解析 Composition Script JSON，组装 TransitionSeries + BGM 音轨。

```tsx
// 伪代码——展示组装逻辑
const DynamicVideoRenderer: React.FC<CompositionScript> = (script) => {
  return (
    <AbsoluteFill style={{ backgroundColor: script.globalStyle.backgroundColor }}>
      {/* 全局 BGM */}
      {script.bgm && (
        <Audio
          src={script.bgm.src}
          volume={(f) =>
            // fadeIn + hold + fadeOut 音量曲线
            computeBgmVolume(f, script.bgm)
          }
          loop={script.bgm.loop}
        />
      )}

      {/* 场景序列 + 转场 */}
      <TransitionSeries>
        {script.scenes.map((scene, i) => {
          const trans = script.transitions.find(t => t.fromSceneIndex === i);
          return (
            <React.Fragment key={scene.sceneId}>
              <TransitionSeries.Sequence durationInFrames={scene.durationInFrames}>
                <SceneRenderer scene={scene} />
              </TransitionSeries.Sequence>
              {trans && (
                <TransitionSeries.Transition
                  presentation={resolvePresentation(trans)}
                  timing={resolveTiming(trans)}
                />
              )}
            </React.Fragment>
          );
        })}
      </TransitionSeries>
    </AbsoluteFill>
  );
};
```

### 5.7.2 SceneRenderer（场景渲染器）

职责：遍历场景内的 layers，根据 `preset` ID 从注册表取出组件并传入 params。

```tsx
const SceneRenderer: React.FC<{ scene: Scene }> = ({ scene }) => {
  return (
    <AbsoluteFill>
      {scene.layers.map((layer) => {
        const { component: Comp } = getPreset(layer.preset);
        return (
          <Sequence
            key={layer.layerId}
            from={layer.enterAtFrame ?? 0}
            durationInFrames={layer.durationInFrames ?? scene.durationInFrames}
            layout="none"
          >
            <Comp {...layer.params} />
          </Sequence>
        );
      })}
    </AbsoluteFill>
  );
};
```

### 5.7.3 calculateMetadata（动态元数据）

```tsx
const calculateMetadata: CalculateMetadataFunction<CompositionScript> = async ({ props }) => {
  const totalFrames = props.scenes.reduce((sum, s) => sum + s.durationInFrames, 0);
  const transOverlap = props.transitions.reduce(
    (sum, t) => sum + (t.params.durationInFrames || 0), 0
  );
  return {
    durationInFrames: totalFrames - transOverlap,
    width: props.canvas.width,
    height: props.canvas.height,
    fps: props.canvas.fps,
  };
};
```

## 5.8 渲染服务 HTTP 接口规约

### POST /render

请求体：
```jsonc
{
  "taskId": "render_xxx",
  "compositionScript": { /* 完整的 Composition Script JSON */ },
  "outputFormat": "mp4",
  "codec": "h264"
}
```

响应（同步返回任务受理）：
```jsonc
{ "taskId": "render_xxx", "status": "QUEUED" }
```

### GET /render/:taskId/status

响应：
```jsonc
{
  "taskId": "render_xxx",
  "status": "RENDERING",         // QUEUED | RENDERING | DONE | FAILED
  "progress": 0.45,              // 0.0 ~ 1.0
  "outputPath": null,            // DONE 时返回文件路径
  "error": null
}
```

## 5.9 后端编排服务设计

### 5.9.1 新增类清单

| 类名 | 层级 | 职责 |
|------|------|------|
| `RenderOrchestrationService` | Service | 总编排：聚合上下文 → LLM → 校验 → 投递渲染 |
| `CompositionScriptGenerator` | Service | 调用 Ark LLM 产出编排 JSON |
| `RemotionRenderClient` | Infrastructure | HTTP 客户端，对接渲染微服务 |
| `RenderTask` | Entity | 渲染任务持久化（taskId, status, outputPath） |
| `CompositionScriptDTO` | DTO | 编排 JSON 的 Java 映射 |

> 当前实现补充：Remotion 编排链统一通过 `infrastructure/ark` 下的 `ArkPayloadFactory + ArkResponsesClient`
> 调用 Doubao / Ark Chat Completions 协议生成 `CompositionScript`，不再依赖 Spring AI `ChatClient` 的默认模型选择。
> 这样可以避免默认回退到本地 `Ollama/mistral` 一类隐式模型时出现 `model not found` 的配置漂移问题。

### 5.9.2 LLM Prompt 结构要点

系统提示词必须包含：
1. **预设库索引**：所有可用 PresetId 及其参数 Schema 的紧凑描述
2. **协议 Schema**：Composition Script v1 的完整结构定义
3. **一个完整示例**：展示一个 3 场景 + 2 转场 + BGM 的标准输出
4. **字段名与类型约束**：明确要求使用 `durationInFrames`，禁止输出 `durationFrames`；`params` 必须始终是对象，禁止 `null`
5. **空值约束**：`scenes` 必须是非空数组，数组内禁止 `null` 项；没有转场时输出 `transitions: []`，不要输出 `null`

用户提示词包含：
1. 素材清单：每个 PROFILED 素材的 `semanticTags` + `highlights` 摘要
2. 模板结构：已绑定模板的 `segments` + `shots` + `packagingStructure`
3. BGM 信息：推荐的 BGM 路径、时长、BPM

> 当前实现补充：
> 1. Java 编排层会在调用 Remotion 微服务前，对 LLM 产出的 `CompositionScript` 做一次字段别名修复与强校验，例如自动兼容 `durationFrames -> durationInFrames`；
> 2. Remotion 微服务入口会再次使用 Zod Schema 校验请求，提前拦截 `params: null`、缺失 `durationInFrames` 等问题；
> 3. Java 侧会将最近一次成功编排的 `CompositionScript` 及原始模型输出缓存到 Redis，若渲染阶段失败，可直接复用该脚本重新提交渲染，而不必重新调用 LLM。

## 5.10 目录结构

```
d:\Develop\code\bytedance-ai-video\
├── backend/                           # 现有 Spring Boot
│   └── src/.../creation/
│       ├── service/
│       │   └── impl/
│       │       ├── RenderOrchestrationServiceImpl.java
│       │       └── CompositionScriptGenerator.java
│       ├── dto/
│       │   ├── CompositionScriptDTO.java
│       │   └── RenderTaskDTO.java
│       └── infrastructure/
│           └── RemotionRenderClient.java
│
├── remotion-service/                  # [NEW] Remotion 渲染微服务
│   ├── package.json
│   ├── tsconfig.json
│   ├── src/
│   │   ├── Root.tsx                   # Composition 注册
│   │   ├── DynamicVideoRenderer.tsx   # 主渲染器
│   │   ├── SceneRenderer.tsx          # 场景渲染器
│   │   ├── presets/
│   │   │   ├── PresetRegistry.ts
│   │   │   ├── media/
│   │   │   │   ├── VideoClip.tsx
│   │   │   │   ├── ImageLayer.tsx
│   │   │   │   └── AudioTrack.tsx
│   │   │   ├── motion/
│   │   │   │   └── KenBurns.tsx
│   │   │   ├── text/
│   │   │   │   ├── FadeTitle.tsx
│   │   │   │   ├── TypewriterTitle.tsx
│   │   │   │   └── WordHighlightText.tsx
│   │   │   ├── caption/
│   │   │   │   └── Subtitle.tsx
│   │   │   ├── transition/
│   │   │   │   └── resolvers.ts       # fade/slide/wipe 映射函数
│   │   │   └── overlay/
│   │   │       ├── LightLeakWrapper.tsx
│   │   │       ├── FlashOverlay.tsx
│   │   │       ├── BadgePopOverlay.tsx
│   │   │       └── GlowFrameOverlay.tsx
│   │   ├── schemas/
│   │   │   └── CompositionScript.ts   # Zod Schema
│   │   └── server.ts                  # Express HTTP 渲染服务入口
│   └── public/                        # 渲染时的静态资源挂载点
│
└── frontend/                          # 现有 React 前端
    └── src/pages/create/
        └── ProjectRender.tsx          # [NEW] 渲染触发 + 进度展示页
```

## 5.11 实施计划

| 阶段 | 内容 | 预估工时 |
|------|------|---------|
| **P1: 预设库基座** | 搭建 remotion-service 项目，实现 PresetRegistry + 10 个预设组件，用手写 JSON 验证端到端渲染 | 3-5 天 |
| **P2: 渲染服务** | 实现 Express HTTP 入口 + `renderMedia()` 封装 + 任务状态管理 | 2-3 天 |
| **P3: 后端编排** | 实现 CompositionScriptGenerator (LLM Prompt) + RenderOrchestrationService + RemotionRenderClient | 3-5 天 |
| **P4: 前端对接** | ProjectRender 页面：触发渲染 + 进度轮询 + 成片预览/下载 | 2-3 天 |
