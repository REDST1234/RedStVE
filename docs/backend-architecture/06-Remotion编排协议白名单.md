# 06-Remotion编排协议白名单

## 1. 文档定位

本文档是当前系统 **Remotion 编排链** 的白名单协议说明，目标是为以下三类场景提供统一基线：

1. `ArkPromptTemplates.REMOTION_ORCHESTRATOR_JSON` 的提示词约束
2. Java 后端对 `CompositionScript` 的清洗与严格校验
3. Remotion 渲染层对 `preset + params` 的真实消费能力

本文档只覆盖 **Remotion 编排协议**，不覆盖：

1. 视频结构拆解协议
2. 素材画像协议
3. 槽位匹配协议（含 AI 生图补位判定 — 见 [04-创作链路与素材适配模块 §4.6.1](04-创作链路与素材适配模块.md#461-l4-seedream-40-ai-生图补位已实现)）

---

## 2. 顶层协议

### 2.1 根节点

根节点必须是一个 JSON 对象，当前协议名为：

```json
{
  "$schema": "composition-script/v1"
}
```

### 2.2 顶层字段白名单

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `$schema` | `string` | 否 | 推荐固定为 `composition-script/v1` |
| `projectId` | `string` | 否 | 项目标识 |
| `canvas` | `object` | 是 | 目标画布 |
| `globalStyle` | `object` | 否 | 全局视觉样式 |
| `bgm` | `object` | 否 | 全局背景音乐 |
| `scenes` | `array` | 是 | 非空场景数组 |
| `transitions` | `array` | 否 | 场景间转场数组，无转场时建议 `[]` |

禁止输出以上白名单以外的根级字段。

---

## 3. 顶层对象字段

### 3.1 `canvas`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `width` | `integer` | 是 | 正整数 |
| `height` | `integer` | 是 | 正整数 |
| `fps` | `integer` | 否 | 正整数，默认 `30` |

### 3.2 `globalStyle`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `fontFamily` | `string` | 否 | 默认 `Noto Sans SC` |
| `backgroundColor` | `string` | 否 | 建议 hex 颜色 |

### 3.3 `bgm`

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `src` | `string` | 是 | 音频绝对 URL |
| `mixLevel` | `string` | 否 | 只能为 `QUIET` / `BALANCED` / `DRIVE` |
| `volume` | `number` | 否 | 仅历史兼容字段，不建议模型再输出 |
| `loop` | `boolean` | 否 | 默认 `true` |
| `fadeInFrames` | `integer` | 否 | 非负整数 |
| `fadeOutFrames` | `integer` | 否 | 非负整数 |

#### `bgm.mixLevel` 枚举

| 枚举值 | 含义 |
|---|---|
| `QUIET` | 弱背景氛围 |
| `BALANCED` | 常规存在感 |
| `DRIVE` | 强节奏驱动 |

---

## 4. `scenes` 协议

### 4.1 `scene` 字段白名单

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `sceneId` | `string` | 是 | 场景唯一标识 |
| `sceneIndex` | `integer` | 是 | 场景序号 |
| `role` | `string` | 否 | 语义角色，如 `hook/body/climax/outro` |
| `durationInFrames` | `integer` | 是 | 正整数 |
| `layers` | `array` | 是 | 场景图层数组 |

### 4.2 `layer` 字段白名单

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `layerId` | `string` | 是 | 图层唯一标识 |
| `preset` | `string` | 是 | 必须来自预设白名单 |
| `enterAtFrame` | `integer` | 否 | 非负整数，默认 `0` |
| `durationInFrames` | `integer` | 否 | 正整数 |
| `params` | `object` | 是 | 必须是对象，禁止 `null` |

---

## 5. `transitions` 协议

### 5.1 `transition` 字段白名单

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `fromSceneIndex` | `integer` | 是 | 起始场景索引 |
| `toSceneIndex` | `integer` | 是 | 目标场景索引 |
| `preset` | `string` | 是 | `transition.fade` / `transition.slide` / `transition.wipe` |
| `params` | `object` | 是 | 转场参数对象 |
| `overlay` | `object` | 否 | 转场叠加层 |

### 5.2 `transition.params`

通用字段：

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `durationInFrames` | `integer` | 否 | 正整数，默认 `15` |
| `timing` | `string` | 否 | 只能为 `linear` / `spring` |

仅 `transition.slide` 允许：

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `direction` | `string` | 否 | `from-left` / `from-right` / `from-top` / `from-bottom` |

### 5.3 `transition.overlay`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `preset` | `string` | 是 | 必须是已注册 overlay preset |
| `params` | `object` | 是 | overlay 参数对象 |

---

## 6. 预设 ID 白名单

当前注册表中的 `PresetId` 白名单如下：

### 6.1 背景

1. `bg.mesh_gradient`
2. `bg.tech_grid`
3. `bg.noise_grain`

### 6.2 媒体

1. `media.video`
2. `media.image`
3. `media.audio`

### 6.3 运镜

1. `motion.ken_burns`
2. `motion.float_2d5`
3. `motion.parallax_drift`
4. `motion.perspective_tilt`

### 6.4 文本

1. `text.fade_title`
2. `text.kinetic_pop`
3. `text.hero_billboard`
4. `text.typewriter`
5. `text.mask_reveal`
6. `text.word_highlight`
7. `text.counter_number`
8. `text.label_chip`

### 6.5 字幕

1. `caption.subtitle`

### 6.6 转场

1. `transition.fade`
2. `transition.slide`
3. `transition.wipe`

### 6.7 包装层

1. `overlay.light_leak`
2. `overlay.flash`
3. `overlay.badge_pop`
4. `overlay.glow_frame`

### 6.8 底板

1. `backing.solid_plate`
2. `backing.capsule`
3. `backing.glass_plate`

---

## 7. 公共枚举白名单

### 7.1 `layoutMode`

适用于背景和多数文字组件：

1. `auto`
2. `portrait`
3. `landscape`

### 7.2 `positionPreset`

适用于 `text.fade_title` / `text.kinetic_pop` / `text.typewriter` / `text.mask_reveal` / `text.word_highlight`：

1. `hero_top`
2. `hero_center`
3. `hero_lower`
4. `left_focus`
5. `right_focus`

### 7.3 `style.objectFit`

适用于 `media.image` / `media.video` 的 `style`：

1. `cover`
2. `contain`
3. `fill`
4. `none`

### 7.4 `caption.subtitle.position`

1. `bottom_center`
2. `top_center`
3. `center`

### 7.5 `media.audio.audioRole`

1. `sfx_loop`
2. `sfx_cue`
3. `voice_support`

### 7.6 `media.audio.cueType`

1. `typewriter_loop`
2. `type_end_click`
3. `emphasis_hit`
4. `transition_whoosh`
5. `brand_logo_hit`
6. `ui_click`

### 7.7 `media.audio.syncMode`

1. `match_layer`
2. `match_typing`
3. `trigger_on_start`
4. `trigger_on_end`
5. `trigger_on_typing_end`

### 7.8 `text.label_chip.variant`

1. `filled`
2. `outlined`
3. `soft`

### 7.9 `backing.*.position`

`backing.solid_plate` / `backing.capsule` / `backing.glass_plate` 共用同一套语义位置写法，与 `media.image` 的语义 position 协议保持一致：

1. `{ "x": "center", "y": "center" }` — 居中
2. `{ "x": "12%", "y": "30%" }` — 百分比定位
3. `{ "x": 120, "y": 80 }` — 数字像素定位

`x` / `y` 允许的值类型：

1. `"center"` — 自动居中
2. 百分比字符串，如 `"15%"`
3. 数字

渲染层归一后的行为与 `media.image` 语义位置一致（`position: absolute` + `left/top` + 必要时 `translate(-50%, -50%)`）。

---

## 8. 各预设参数协议

## 8.1 `bg.mesh_gradient`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `colors` | `string[]` | 否 | 至少 1 个颜色，组件会补足到 4 个 |
| `intensity` | `number` | 否 | 建议 `0.0 ~ 1.2` |
| `blendMode` | `string` | 否 | 当前是 CSS `mixBlendMode` 字符串，不是后端枚举 |
| `layoutMode` | `string` | 否 | `auto` / `portrait` / `landscape` |

## 8.2 `bg.tech_grid`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `backgroundColor` | `string` | 否 | 建议 hex |
| `lineColor` | `string` | 否 | 建议 hex |
| `accentColor` | `string` | 否 | 建议 hex |
| `gridSize` | `number` | 否 | 建议正数 |
| `lineOpacity` | `number` | 否 | 建议 `0.0 ~ 1.0` |
| `lineWidth` | `number` | 否 | 建议正数 |
| `driftSpeed` | `number` | 否 | 建议非负数 |
| `layoutMode` | `string` | 否 | `auto` / `portrait` / `landscape` |

## 8.3 `bg.noise_grain`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `backgroundColor` | `string` | 否 | 建议 hex 或 `transparent` |
| `grainOpacity` | `number` | 否 | 建议 `0.0 ~ 1.0` |
| `scale` | `number` | 否 | 建议正数 |
| `layoutMode` | `string` | 否 | `auto` / `portrait` / `landscape` |

## 8.4 `media.image`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `src` | `string` | 是 | 图片 URL |
| `style` | `object` | 否 | 见“媒体 style 协议” |

## 8.5 `media.video`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `src` | `string` | 是 | 视频 URL |
| `trimBefore` | `number` | 否 | 非负数 |
| `trimAfter` | `number` | 否 | 非负数 |
| `volume` | `number` | 否 | 建议 `0.0 ~ 1.0` |
| `playbackRate` | `number` | 否 | 建议正数 |
| `loop` | `boolean` | 否 | 布尔值 |
| `muted` | `boolean` | 否 | 布尔值 |
| `style` | `object` | 否 | 见“媒体 style 协议” |

## 8.6 `media.audio`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `src` | `string` | 是 | 音频 URL |
| `volume` | `number` | 否 | 建议 `0.0 ~ 1.0` |
| `loop` | `boolean` | 否 | 布尔值 |
| `trimBefore` | `number` | 否 | 非负数 |
| `trimAfter` | `number` | 否 | 非负数 |
| `fadeInFrames` | `integer` | 否 | 非负整数 |
| `fadeOutFrames` | `integer` | 否 | 非负整数 |
| `totalDurationFrames` | `integer` | 否 | 正整数 |
| `audioRole` | `string` | 否 | 见 `media.audio.audioRole` 白名单 |
| `cueType` | `string` | 否 | 见 `media.audio.cueType` 白名单 |
| `syncWithLayerId` | `string` | 否 | 目标 layerId |
| `syncMode` | `string` | 否 | 见 `media.audio.syncMode` 白名单 |
| `offsetFrames` | `integer` | 否 | 可正可负的相对偏移帧 |

### 同步语义

1. `match_layer`
- 与目标层同时开始
- 与目标层同时结束

2. `match_typing`
- 与目标层同时开始
- 若目标层是 `text.typewriter`，持续到实际打字结束
- 优先用于打字机持续音

3. `trigger_on_start`
- 在目标层开始时触发
- 时长由当前音效层自身 `durationInFrames` 或 `totalDurationFrames` 决定

4. `trigger_on_end`
- 在目标层结束时触发
- 时长由当前音效层自身 `durationInFrames` 或 `totalDurationFrames` 决定

5. `trigger_on_typing_end`
- 在 `text.typewriter` 打字结束时触发
- 优先用于结束 click / hit

### 推荐搭配

1. 打字机持续音
- `audioRole: "sfx_loop"`
- `cueType: "typewriter_loop"`
- `syncWithLayerId: "<typewriter-layer-id>"`
- `syncMode: "match_typing"`

2. 打字结束提示音
- `audioRole: "sfx_cue"`
- `cueType: "type_end_click"`
- `syncWithLayerId: "<typewriter-layer-id>"`
- `syncMode: "trigger_on_typing_end"`

3. 关键词击中音
- `audioRole: "sfx_cue"`
- `cueType: "emphasis_hit"`
- `syncWithLayerId: "<target-layer-id>"`
- `syncMode: "trigger_on_start"` 或 `trigger_on_end`

## 8.7 `motion.ken_burns`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `src` | `string` | 是 | 图片 URL |
| `startScale` | `number` | 否 | 建议正数 |
| `endScale` | `number` | 否 | 建议正数 |
| `startPosition` | `object` | 否 | `{ x:number, y:number }` |
| `endPosition` | `object` | 否 | `{ x:number, y:number }` |
| `easing` | `number[4]` | 否 | 三次贝塞尔控制点数组 |

## 8.8 `motion.float_2d5`

### 定位

2.5D 悬浮卡片。通过正弦振荡实现上下浮动、左右摇摆和动态深度阴影变化，模拟图片悬浮在 3D 空间中的视觉效果。适合 UI 元素、贴纸、LOGO、插图等需要「漂浮在空中」视觉感受的静态图片。配合 `backing.glass_plate` 使用层次感更佳。

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `src` | `string` | 是 | 图片 URL |
| `floatAmplitude` | `number` | 否 | Y 轴浮动幅度 (px)，默认 `12`，建议 6~20 |
| `floatSpeed` | `number` | 否 | 浮动速度倍率，默认 `0.7`，范围 0.3~2.0 |
| `swayAmount` | `number` | 否 | 左右摇摆角度 (deg)，默认 `2`，建议 1~5 |
| `scaleBreath` | `number` | 否 | 缩放呼吸幅度，默认 `0.03`，0=关闭 |
| `perspective` | `number` | 否 | CSS perspective (px)，默认 `800` |
| `shadowEnabled` | `boolean` | 否 | 是否启用动态深度阴影，默认 `true` |
| `shadowColor` | `string` | 否 | 阴影颜色，默认 `rgba(0,0,0,0.25)` |
| `objectFit` | `string` | 否 | `cover` / `contain` / `fill` / `none`，默认 `contain` |
| `scale` | `number` | 否 | 图片相对于画布的缩放，默认 `0.9` |

### 动画行为

1. `translateY` 正弦振荡 (频率由 `floatSpeed` 控制)：模拟上下浮动
2. `rotateZ` 正弦振荡 (相位偏移 π/3)：模拟左右摇摆，不与浮动同步
3. `scale` 正弦振荡 (相位偏移 π/2)：模拟呼吸感
4. `filter: drop-shadow` 动态偏移 + 模糊：浮动越高阴影越远越淡
5. `rotateX(4deg)` + `perspective`：微妙的 3D 远近感

### LLM 使用规则

- 适合静态图片需要动感但不适合 ken_burns 推拉的场景
- UI_ELEMENT / STICKER / LOGO 类素材优先使用
- 配合 `backing.glass_plate` 或 `backing.solid_plate` 做背景衬底，层次感更强
- 若需要更强的漂浮感，可适当增大 `floatAmplitude` 和 `swayAmount`
- 若素材是 LOGO / 图标 / 挂件图，优先 `objectFit: contain`

## 8.9 `motion.parallax_drift`

### 定位

视差漂移。图片沿 X/Y 轴以不同频率正弦漂移，产生李萨如轨迹式的深度移动感。适合背景图或大面积插图需要「缓慢移动的深度感」的场景。与 `motion.ken_burns` 的区别：parallax_drift 是连续循环漂移，没有明确的起始/结束状态，更适合作为氛围背景。

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `src` | `string` | 是 | 图片 URL |
| `driftRangeX` | `number` | 否 | X 轴漂移范围 (px)，默认 `30`，建议 10~60 |
| `driftRangeY` | `number` | 否 | Y 轴漂移范围 (px)，默认 `20`，建议 8~40 |
| `driftSpeedX` | `number` | 否 | X 方向速度倍率，默认 `0.5`，范围 0.3~2.0 |
| `driftSpeedY` | `number` | 否 | Y 方向速度倍率，默认 `0.7`，范围 0.3~2.0 |
| `scaleRange` | `number` | 否 | 缩放波动幅度，默认 `0.05`，0=关闭 |
| `rotationRange` | `number` | 否 | 旋转波动幅度 (deg)，默认 `1.5`，0=关闭 |
| `objectFit` | `string` | 否 | `cover` / `contain` / `fill` / `none`，默认 `cover` |

### 动画行为

1. X 和 Y 使用不同基础周期 (速度不同)，产生非同步正弦漂移
2. 使用比画布大 10% 的容器避免漂移时露出边缘
3. 缩放和旋转使用各自的周期叠加，进一步增强不规则运动感

### LLM 使用规则

- 适合 BACKGROUND / ILLUSTRATION 类素材
- 作为场景底层的氛围动效，文字层可叠在上方
- `driftSpeedX` 和 `driftSpeedY` 建议取不同值 (如 0.5/0.7)，避免对角线直线运动
- 若需要更明显或更微妙的漂移，调整 `driftRangeX`/`driftRangeY` 而非 speed
- 不适合需要精确构图对齐的场景（素材会持续移动）

## 8.10 `motion.perspective_tilt`

### 定位

CSS 3D 透视倾斜。通过 `perspective` + `rotateX` + `rotateY` 实现卡片式 3D 透视倾斜效果，类似 Apple TV 卡片悬停或 iOS App Store 卡片风格。适合需要「高端质感」「3D 卡片」视觉的品牌展示、产品图、封面图场景。

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `src` | `string` | 是 | 图片 URL |
| `rotateX` | `number` | 否 | X 轴旋转角度 (deg)，默认 `-5`，建议 -15~15 |
| `rotateY` | `number` | 否 | Y 轴旋转角度 (deg)，默认 `3`，建议 -15~15 |
| `perspective` | `number` | 否 | CSS perspective (px)，默认 `1000`，建议 600~2000 |
| `scale` | `number` | 否 | 缩放比例，默认 `0.85` |
| `dynamicEnabled` | `boolean` | 否 | 是否缓慢微动，默认 `true` |
| `dynamicRange` | `number` | 否 | 微动幅度 (deg)，默认 `2`，建议 1~5 |
| `shadowEnabled` | `boolean` | 否 | 投影开关，默认 `true` |
| `shadowColor` | `string` | 否 | 投影颜色，默认 `rgba(0,0,0,0.3)` |
| `objectFit` | `string` | 否 | `cover` / `contain` / `fill` / `none`，默认 `contain` |

### 动画行为

1. 静态倾斜：`rotateX` + `rotateY` 恒定角度
2. 动态微动（`dynamicEnabled: true`）：X/Y 角度叠加缓慢正弦振荡（不同周期），产生呼吸式微动
3. 深度阴影：`boxShadow` 多层叠加增强 3D 纵深感
4. 图片带 `borderRadius: 12px` 圆角柔和边缘

### LLM 使用规则

- 适合高端品牌展示、产品图、封面图
- `rotateX` 负值 = 顶部后倾（常见透视），正值 = 顶部前倾
- `rotateY` 正值 = 右侧微转，负值 = 左侧微转
- 配合 `backing.glass_plate` 做背景，层次感更突出
- 不适合需要正视/平视的 UI 元素或文字类素材

## 8.11 `text.fade_title`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `text` | `string` | 是 | 标题文本 |
| `fontSize` | `number` | 否 | 建议正数 |
| `color` | `string` | 否 | 建议 hex |
| `fontWeight` | `number` | 否 | 建议正数 |
| `position` | `object` | 否 | `{ x, y }` 语义位置 |
| `positionPreset` | `string` | 否 | 见 `positionPreset` 白名单 |
| `layoutMode` | `string` | 否 | 见 `layoutMode` 白名单 |
| `easing` | `number[4]` | 否 | 三次贝塞尔控制点数组 |
| `textShadow` | `string` | 否 | CSS 文本阴影字符串 |
| `maxWidth` | `string/number` | 否 | 最大宽度 |

## 8.12 `text.kinetic_pop`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `text` | `string` | 是 | 文本 |
| `fontSize` | `number` | 否 | 正数 |
| `color` | `string` | 否 | 建议 hex |
| `fontWeight` | `number` | 否 | 正数 |
| `position` | `object` | 否 | `{ x, y }` |
| `positionPreset` | `string` | 否 | 见白名单 |
| `layoutMode` | `string` | 否 | 见白名单 |
| `scaleFrom` | `number` | 否 | 建议正数 |
| `scaleTo` | `number` | 否 | 建议正数 |
| `rotationFrom` | `number` | 否 | 数值角度 |
| `rotationTo` | `number` | 否 | 数值角度 |
| `enterFrames` | `integer` | 否 | 正整数 |
| `settleFrames` | `integer` | 否 | 正整数 |
| `textShadow` | `string` | 否 | CSS 文本阴影 |
| `letterSpacing` | `string/number` | 否 | 字距 |
| `textAlign` | `string` | 否 | `left` / `center` / `right` |
| `maxWidth` | `string/number` | 否 | 最大宽度 |

## 8.13 `text.hero_billboard`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `text` | `string` | 否 | 单句文案 |
| `texts` | `string[]` | 否 | 多句文案 |
| `layoutPattern` | `string` | 否 | 见枚举白名单 |
| `animationMode` | `string` | 否 | 见枚举白名单 |
| `easingPreset` | `string` | 否 | 见枚举白名单 |
| `layoutMode` | `string` | 否 | 见 `layoutMode` 白名单 |
| `fontSize` | `number` | 否 | 正数 |
| `color` | `string` | 否 | 建议 hex |
| `accentColor` | `string` | 否 | 建议 hex |
| `fontWeight` | `number` | 否 | 正数 |
| `letterSpacing` | `string/number` | 否 | 字距 |
| `textShadow` | `string` | 否 | CSS 文本阴影 |
| `strokeEnabled` | `boolean` | 否 | 布尔值 |
| `strokeColor` | `string` | 否 | 建议 hex |
| `strokeWidth` | `number` | 否 | 正数 |
| `glowColor` | `string` | 否 | 建议 hex |
| `glowBlur` | `number` | 否 | 非负数 |
| `glowOpacity` | `number` | 否 | 建议 `0.0 ~ 1.0` |
| `maxWidth` | `string/number` | 否 | 最大宽度 |
| `charIntervalFrames` | `integer` | 否 | 正整数 |
| `staggerFrames` | `integer` | 否 | 正整数 |
| `lineGap` | `number` | 否 | 非负数 |

#### `text.hero_billboard.layoutPattern`

1. `center_focus`
2. `four_corners`
3. `triangle_stack`
4. `top_bottom_split`
5. `left_right_balance`

#### `text.hero_billboard.animationMode`

1. `whole_pop`
2. `char_stagger`
3. `type_reveal`

#### `text.hero_billboard.easingPreset`

1. `spring_bounce`
2. `expo_out`
3. `linear_fade`

## 8.14 `text.typewriter`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `text` | `string` | 是 | 文本 |
| `fontSize` | `number` | 否 | 正数 |
| `color` | `string` | 否 | 建议 hex |
| `fontWeight` | `number` | 否 | 正数 |
| `position` | `object` | 否 | `{ x, y }` |
| `positionPreset` | `string` | 否 | 见白名单 |
| `layoutMode` | `string` | 否 | 见白名单 |
| `charIntervalFrames` | `integer` | 否 | 正整数 |
| `cursor` | `string` | 否 | 光标字符 |
| `cursorColor` | `string` | 否 | 建议 hex |
| `cursorScale` | `number` | 否 | 正数 |
| `textShadow` | `string` | 否 | CSS 文本阴影 |
| `maxWidth` | `string/number` | 否 | 最大宽度 |
| `letterSpacing` | `string/number` | 否 | 字距 |
| `textAlign` | `string` | 否 | `left` / `center` / `right` |

## 8.15 `text.mask_reveal`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `text` | `string` | 是 | 文本 |
| `fontSize` | `number` | 否 | 正数 |
| `color` | `string` | 否 | 建议 hex |
| `fontWeight` | `number` | 否 | 正数 |
| `position` | `object` | 否 | `{ x, y }` |
| `positionPreset` | `string` | 否 | 见白名单 |
| `layoutMode` | `string` | 否 | 见白名单 |
| `revealDirection` | `string` | 否 | 见枚举白名单 |
| `revealFrames` | `integer` | 否 | 正整数 |
| `textShadow` | `string` | 否 | CSS 文本阴影 |
| `letterSpacing` | `string/number` | 否 | 字距 |
| `textAlign` | `string` | 否 | `left` / `center` / `right` |
| `maxWidth` | `string/number` | 否 | 最大宽度 |
| `maskPadding` | `number` | 否 | 非负数 |

#### `text.mask_reveal.revealDirection`

1. `left_to_right`
2. `right_to_left`
3. `bottom_to_top`
4. `top_to_bottom`

## 8.16 `text.word_highlight`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `text` | `string` | 否 | 文本 |
| `tokens` | `string[]` | 否 | 自定义 token 数组 |
| `highlightWords` | `string[]` | 否 | 高亮词数组 |
| `fontSize` | `number` | 否 | 正数 |
| `color` | `string` | 否 | 建议 hex |
| `highlightColor` | `string` | 否 | 建议 hex |
| `highlightBackground` | `string` | 否 | 允许 hex 或 rgba |
| `fontWeight` | `number` | 否 | 正数 |
| `position` | `object` | 否 | `{ x, y }` |
| `positionPreset` | `string` | 否 | 见白名单 |
| `layoutMode` | `string` | 否 | 见白名单 |
| `wordDurationInFrames` | `integer` | 否 | 正整数 |
| `textShadow` | `string` | 否 | CSS 文本阴影 |
| `gap` | `number` | 否 | 非负数 |
| `textAlign` | `string` | 否 | `left` / `center` / `right` |
| `highlightScale` | `number` | 否 | 正数 |

## 8.17 `caption.subtitle`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `text` | `string` | 是 | 字幕文本 |
| `fontSize` | `number` | 否 | 正数 |
| `color` | `string` | 否 | 建议 hex |
| `bgColor` | `string` | 否 | 允许 hex 或 rgba |
| `position` | `string` | 否 | `bottom_center` / `top_center` / `center` |

## 8.18 `overlay.light_leak`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `seed` | `number` | 否 | 数值 |
| `hueShift` | `number` | 否 | 数值 |
| `durationInFrames` | `integer` | 否 | 正整数 |

## 8.19 `overlay.flash`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `color` | `string` | 否 | 建议 hex |
| `maxOpacity` | `number` | 否 | 建议 `0.0 ~ 1.0` |
| `enterFrames` | `integer` | 否 | 正整数 |
| `holdFrames` | `integer` | 否 | 非负整数 |
| `exitFrames` | `integer` | 否 | 正整数 |

## 8.20 `overlay.badge_pop`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `text` | `string` | 是 | 角标文本 |
| `bgColor` | `string` | 否 | 建议 hex |
| `color` | `string` | 否 | 建议 hex |
| `fontSize` | `number` | 否 | 正数 |
| `fontWeight` | `number` | 否 | 正数 |
| `position` | `object` | 否 | `{ x, y }` |
| `scaleFrom` | `number` | 否 | 正数 |
| `scaleTo` | `number` | 否 | 正数 |
| `rotation` | `number` | 否 | 数值角度 |
| `paddingX` | `number` | 否 | 非负数 |
| `paddingY` | `number` | 否 | 非负数 |
| `borderRadius` | `number` | 否 | 非负数 |
| `shadowColor` | `string` | 否 | 建议 rgba |
| `borderColor` | `string` | 否 | 建议 rgba 或 hex |

## 8.21 `overlay.glow_frame`

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `color` | `string` | 否 | 建议 hex |
| `thickness` | `number` | 否 | 正数 |
| `glowBlur` | `number` | 否 | 非负数 |
| `opacity` | `number` | 否 | 建议 `0.0 ~ 1.0` |
| `borderRadius` | `number` | 否 | 非负数 |
| `pulseStrength` | `number` | 否 | 建议 `0.0 ~ 1.0` |
| `inset` | `number` | 否 | 非负数 |

## 8.22 `text.counter_number`

### 定位

数字滚动计数器。适用场景：价格强调、数据展示、进度数字、倒计时。每个数字位独立纵向滚动，从高位到低位级联触发。

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `value` | `number` | 是 | 目标数值 |
| `prefix` | `string` | 否 | 前缀字符串 |
| `suffix` | `string` | 否 | 后缀字符串 |
| `decimals` | `number` | 否 | 小数位数，默认 `0` |
| `fontSize` | `number` | 否 | 正数 |
| `color` | `string` | 否 | 建议 hex |
| `fontWeight` | `number` | 否 | 正数 |
| `scrollFrames` | `integer` | 否 | 数字滚动持续帧数，默认 `30` |
| `digitGap` | `number` | 否 | 数字间距，默认 `4` |
| `position` | `object` | 否 | `{ x, y }` 语义位置 |
| `layoutMode` | `string` | 否 | `auto` / `portrait` / `landscape` |
| `textShadow` | `string` | 否 | CSS 文本阴影 |
| `letterSpacing` | `string/number` | 否 | 字距 |

### 动画行为

1. 每个数字位独立创建滚动容器
2. 从最高位到最低位依次触发，间隔约 `scrollFrames / 4` 帧
3. 每位数字：当前数字向上滚出并淡出，目标数字从下方滚入并淡入
4. 前缀/后缀符号不参与滚动，固定显示
5. 小数点固定显示，不参与滚动

### LLM 使用规则

- 适用 5~11 位数字；超出建议拆分为多个 `counter_number` 图层
- 前缀/后缀不超过 3 个字符
- 小数位建议 ≤ 2
- 建议配合 `backing.glass_plate` 或 `backing.solid_plate` 做背景衬底

## 8.23 `text.label_chip`

### 定位

信息标签芯片。适用场景：品类标签、特性标注、阶段标记、关键词胶囊。与 `overlay.badge_pop` 的区别：`label_chip` 是轻量 pop-in，无旋转无弹跳，适合信息标注而非促销冲击。

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `text` | `string` | 是 | 标签文本 |
| `variant` | `string` | 否 | `filled` / `outlined` / `soft`，默认 `filled` |
| `color` | `string` | 否 | 建议 hex |
| `bgColor` | `string` | 否 | 建议 hex（filled/soft 模式有效） |
| `borderColor` | `string` | 否 | 建议 hex（outlined 模式有效） |
| `fontSize` | `number` | 否 | 正数 |
| `fontWeight` | `number` | 否 | 正数 |
| `paddingX` | `number` | 否 | 非负数 |
| `paddingY` | `number` | 否 | 非负数 |
| `borderRadius` | `number` | 否 | 默认 `999`（胶囊形） |
| `position` | `object` | 否 | `{ x, y }` 语义位置 |
| `icon` | `string` | 否 | 前置 emoji 字符 |
| `enterFrames` | `integer` | 否 | 入场帧数，默认 `14` |
| `textShadow` | `string` | 否 | CSS 文本阴影 |

### variant 语义

| 枚举值 | 视觉表现 |
|---|---|
| `filled` | 实色背景 + 白色文字 + 无边框 |
| `outlined` | 透明背景 + 1.5px 描边 + 继承色文字 |
| `soft` | 低不透明度背景 (0.18) + 无边框 + 继承色文字 |

### 动画行为

`scale(0.85 → 1.0)` + `opacity(0 → 1)` 轻量 pop-in，无旋转无弹跳无浮动。

### LLM 使用规则

- 短文本（2~10 字），不用于长段落
- 优先放在标题旁或数据下方做语义标注
- 同一场景建议 ≤ 4 个 chip，避免信息过载
- 若需要强冲击感（如促销角标），改用 `overlay.badge_pop`

## 8.24 `backing.solid_plate`

### 定位

实色背景底板。为文字/元素提供不透明或半透明背景垫层，提高可读性。适用于所有需要文字衬底的场景。

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `width` | `string/number` | 否 | CSS 宽度，默认 `"auto"` |
| `height` | `string/number` | 否 | CSS 高度，默认 `"auto"` |
| `color` | `string` | 否 | 底板颜色，建议 hex |
| `opacity` | `number` | 否 | 建议 `0.0 ~ 1.0`，默认 `0.75` |
| `borderRadius` | `number` | 否 | 非负数，默认 `20` |
| `padding` | `number` | 否 | 内边距，`"auto"` 模式生效，默认 `32` |
| `position` | `object` | 否 | `{ x, y }` 语义位置 |
| `borderColor` | `string` | 否 | 描边色，建议 hex |
| `borderWidth` | `number` | 否 | 描边宽度，默认 `0` |
| `shadowEnabled` | `boolean` | 否 | 是否投影，默认 `true` |
| `shadowColor` | `string` | 否 | 投影色，建议 rgba |
| `enterFrames` | `integer` | 否 | 入场淡入帧数，默认 `12` |

### auto 尺寸模式

当 `width` / `height` 为 `"auto"` 时，底板尺寸由子元素内容撑开，`padding` 生效。当显式指定像素或百分比时，`padding` 不生效。

### LLM 使用规则

- 作为 scene 内第一个 layer 放置，其他 layer 通过 `enterAtFrame` 叠在其上方
- 不要和 `backing.glass_plate` 同时叠加在同一区域
- 暗色场景推荐 `color: "#0B1120"` + `opacity: 0.65`，亮色场景反之

## 8.25 `backing.capsule`

### 定位

胶囊形背景底板。`backing.solid_plate` 的圆角变体，默认 `borderRadius: 999`。适合衬在短标题、关键词、单行文本后面。

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `width` | `string/number` | 否 | CSS 宽度，默认 `"auto"` |
| `height` | `string/number` | 否 | CSS 高度，默认 `"auto"` |
| `color` | `string` | 否 | 胶囊颜色，建议 hex |
| `opacity` | `number` | 否 | 建议 `0.0 ~ 1.0`，默认 `0.85` |
| `borderRadius` | `number` | 否 | 非负数，默认 `999`（完全胶囊） |
| `paddingX` | `number` | 否 | 水平内边距，默认 `28` |
| `paddingY` | `number` | 否 | 垂直内边距，默认 `14` |
| `position` | `object` | 否 | `{ x, y }` 语义位置 |
| `borderColor` | `string` | 否 | 描边色，建议 hex |
| `borderWidth` | `number` | 否 | 描边宽度，默认 `0` |
| `shadowEnabled` | `boolean` | 否 | 是否投影，默认 `true` |
| `shadowColor` | `string` | 否 | 投影色，建议 rgba |
| `enterFrames` | `integer` | 否 | 入场帧数，默认 `12` |
| `scaleFrom` | `number` | 否 | 入场起始缩放，默认 `0.92` |

### 动画行为

`scale(0.92 → 1.0)` + `opacity(0 → 1)`，带 `spring_bounce` easing。

### LLM 使用规则

- 只用于短文本（≤ 15 字），不适合长段落
- 建议与 `text.kinetic_pop` 或 `text.label_chip` 搭配使用
- `position` 应与目标文字层对齐（或略偏移做层次感）

## 8.26 `backing.glass_plate`

### 定位

玻璃磨砂底板。iOS-style 毛玻璃效果背景，适用于高端质感场景。⚠️ Remotion SSR 环境不支持 CSS `backdrop-filter`，渲染层通过渐变叠加 + SVG noise 纹理降级模拟。

### 字段白名单

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `width` | `string/number` | 否 | CSS 宽度，默认 `"auto"` |
| `height` | `string/number` | 否 | CSS 高度，默认 `"auto"` |
| `blurAmount` | `number` | 否 | 非负数，控制 noise 颗粒度，默认 `20` |
| `tintColor` | `string` | 否 | 玻璃色相叠加，建议 rgba |
| `borderRadius` | `number` | 否 | 非负数，默认 `24` |
| `borderColor` | `string` | 否 | 玻璃边缘光线色，建议 rgba |
| `borderWidth` | `number` | 否 | 边缘光线宽度，默认 `1` |
| `padding` | `number` | 否 | 内边距，默认 `36` |
| `position` | `object` | 否 | `{ x, y }` 语义位置 |
| `shadowEnabled` | `boolean` | 否 | 是否投影，默认 `true` |
| `shadowColor` | `string` | 否 | 投影色，建议 rgba |
| `enterFrames` | `integer` | 否 | 入场帧数，默认 `14` |

### 渲染层降级方案

1. 主体：`background: linear-gradient(135deg, rgba(255,255,255,0.18), rgba(255,255,255,0.04))`
2. 纹理：内层 SVG noise 叠加（复用 `bg.noise_grain` 的纹理生成逻辑，`opacity: 0.08 ~ 0.14`）
3. 边缘：`border: 1px solid rgba(255,255,255,0.18)` 模拟玻璃高光边缘
4. 投影：`boxShadow: 0 24px 64px rgba(0,0,0,0.18)` 制造悬浮感

### LLM 使用规则

- 适合暗色场景 + 亮色文字的组合（玻璃效果最明显）
- 建议作为 scene 第一个 layer，其余文字 layer 叠在上方
- `tintColor` 应与场景主色调协调，默认偏冷白 `rgba(255,255,255,0.12)`
- 同一 scene 内建议只用一个 glass_plate，避免叠加后模糊效果失真

---

## 9. 媒体 `style` 协议

`media.image.params.style` 与 `media.video.params.style` 当前允许两类写法：

### 9.1 普通 CSS 样式

常见字段：

1. `width`
2. `height`
3. `objectFit`
4. `position`
5. `top`
6. `left`
7. `right`
8. `bottom`
9. `transform`
10. 其他合法 CSSProperties 字段

### 9.2 语义化位置写法

当前兼容的语义位置输入：

```json
{
  "style": {
    "position": { "x": "center", "y": "center" }
  }
}
```

或历史兼容：

```json
{
  "style": {
    "x": "15%",
    "y": "25%"
  }
}
```

语义位置会被渲染层归一为：

1. `position: absolute`
2. `left/top`
3. 必要时自动追加 `translateX(-50%)` / `translateY(-50%)`
4. 若未显式给 `objectFit`，默认自动改为 `contain`

### 9.3 语义位置取值约束

`x` 与 `y` 允许：

1. `"center"`
2. 百分比字符串，如 `"15%"`
3. 数字

---

## 10. 历史兼容写法

当前系统仍保留少量白名单兼容，以支持旧脚本和平滑迁移。

### 10.1 `durationFrames -> durationInFrames`

兼容别名：

1. `durationFrames`

目标规范：

1. `durationInFrames`

### 10.2 语义位置兼容

兼容：

1. `style.position = { x, y }`
2. `style.x`
3. `style.y`

目标规范：

1. 后续建议统一只输出 `style.position = { x, y }`

### 10.3 `bgm.volume`

兼容：

1. `bgm.volume`

目标规范：

1. 新脚本只输出 `bgm.mixLevel`
2. 后端再将 `mixLevel` 映射成最终 `volume`

---

## 11. 当前最容易漂移的字段

这些字段历史上最容易出现“提示词写了，但组件不认”的情况，后续应优先纳入严格校验：

1. `positionPreset`
2. `layoutPattern`
3. `animationMode`
4. `easingPreset`
5. `revealDirection`
6. `transition.params.timing`
7. `transition.slide.direction`
8. `bgm.mixLevel`
9. `style.objectFit`
10. 语义位置写法 `style.position / style.x / style.y`
11. `text.label_chip.variant`
12. `backing.*.width / height`（`"auto"` vs 像素 vs 百分比）

---

## 12. 建议使用方式

后续所有 Remotion 编排相关能力应优先遵守这份文档：

1. Prompt 只能允许输出本文档白名单中的字段和枚举
2. Java 后端 `sanitizeScript / validateScript` 应按本文档做 schema-aware 校验
3. 若新增 preset，应先更新注册表，再更新本文档，再更新 prompt 与 validator

这份文档的目标不是”描述大概能怎么用”，而是作为 **Remotion 编排协议基线**，帮助我们统一：

1. 模型输出
2. 后端校验
3. 渲染器消费

---

## 13. AI 生成素材在编排中的呈现方式

当槽位匹配阶段判定某 MISSING 槽位可 AI 生图补位并生成成功后，该素材在编排 LLM 的 `assetBrief` 中以 `IMAGE_GENERATED` 资产卡片呈现：

- **assetVariant**: `IMAGE_GENERATED`（区别于 `ORIGINAL` / `ADAPTED`）
- **src**: Seedream 返回的公网 URL，Remotion 侧按普通 `media.image` 预设渲染
- **imageGenCategory**: 生图类别 (`UI_ELEMENT` / `STICKER` / `LOGO` / `ILLUSTRATION` / `BACKGROUND`)
- **imageGenDescription**: 中文自然语言描述，帮助编排 LLM 理解该图的语义角色
- **priorityHint**: 编排优先级建议

Remotion 渲染层无需感知素材来源——AI 生成的图片通过标准的 `media.image` preset + `src` URL 使用，与其他图片素材一致。生图判定逻辑、异步状态机和透明背景约束完全在后端 `AdaptationOrchestratorServiceImpl` 和 `CreationProjectServiceImpl` 中处理，详见 [04-创作链路与素材适配模块](04-创作链路与素材适配模块.md)。
