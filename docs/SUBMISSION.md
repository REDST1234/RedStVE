# RedStVE — 短视频结构迁移与自动化创作引擎

> Red Structure Video Engine

---

## 📌 交付物索引

| 交付物 | 链接 |
|--------|------|
| **代码仓库** | `[待填入仓库 URL]` |
| **演示视频（操作录屏）** | `[待填入演示视频链接]` |
| **产出视频（系统生成的成片）** | `[待填入产出视频链接]` |

---

## 一、项目说明

### 1.1 项目简介

RedStVE 是一个面向短视频领域的**结构迁移与自动化创作系统**。其核心工作流为：

1. **拆解**：对参考样例视频进行多维度拆解（镜头切分、语音识别、关键帧提取）
2. **抽象**：由大语言模型（LLM）从拆解结果中抽象出可复用的结构化模板
3. **匹配**：将用户提供的新素材智能匹配到模板的各个槽位中
4. **适配**：经 FFmpeg 适配处理（裁切、缩放、光照调整等）
5. **渲染**：由 Remotion 程序化渲染引擎合成新视频

### 1.2 技术栈

| 层级 | 技术选型 |
|------|----------|
| 前端 | React 18 + Vite + TypeScript + TailwindCSS |
| 后端 | Java 17 + Spring Boot 3.2.5 + MyBatis-Plus |
| 渲染引擎 | Node.js + Remotion 4 + headless Chromium |
| 数据存储 | MySQL 8.4 + Redis 7 + ChromaDB（向量库） |
| AI 服务 | 火山方舟 Ark LLM API + 豆包 Seed ASR + Seedream（文生图） |
| 音视频处理 | FFmpeg / FFprobe |
| 图像处理 | ComfyUI（背景移除） + Ollama（向量嵌入） |
| 容器化 | Docker Compose |

### 1.3 当前项目局限性

受限于当前版本架构架构和算力开销，系统在以下方面存在硬性约束：
1. **拆解能力限制**：当前项目不支持在一个项目中同时上传并拆解多个视频，只能基于单个参考视频提取结构模板。
2. **素材处理限制**：当前不支持根据多个视频素材进行混合裁剪操作（同一时间线主轨道仅处理 1 段核心视频素材）。
3. **上传数量上限**：用户在单次创作中上传的素材存在严格的数量上限限制：
   - 视频：**最多 1 个**
   - 图片：**最多 5 张**
   - 文案：**最多 10 条**
4. **语音合成限制**：本项目没有引入 TTS（Text-to-Speech，文本转语音）技术，因此对口播类的视频创作局限更大。

---

## 二、整体 AI 架构

### 2.1 系统分层

```
┌─────────────────────────────────────────────────────────────────┐
│                      交互层 (Frontend)                          │
│   React + Vite + react-router-dom                               │
│   拆解看板 / 创作工作流 / BGM 知识图谱 / Remotion Player        │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP (Vite proxy → :8080)
┌────────────────────────────▼────────────────────────────────────┐
│                  业务调度层 (Spring Boot)                        │
│                                                                  │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐   │
│  │ VideoUpload  │  │StructureAnalyzer │  │CreationProject   │   │
│  │ Service      │  │ServiceImpl       │  │ServiceImpl       │   │
│  │              │  │(LLM 结构分析)    │  │(创作编排主控)    │   │
│  └──────┬───────┘  └────────┬─────────┘  └────────┬─────────┘   │
│         │                   │                      │             │
│  ┌──────▼───────┐  ┌───────▼──────────┐  ┌───────▼──────────┐  │
│  │ ASR 异步分析 │  │ Timeline 多路    │  │ SlotMatcher +    │  │
│  │ Scene 异步   │  │ 时间轴归一器     │  │ Adaptation       │  │
│  │ KeyFrame 异步│  │                  │  │ Orchestrator     │  │
│  └──────┬───────┘  └──────────────────┘  └───────┬──────────┘  │
│         │                                         │             │
└─────────┼─────────────────────────────────────────┼─────────────┘
          │                                         │
┌─────────▼─────────────────────────────────────────▼─────────────┐
│                     原子引擎层 (Engine)                          │
│                                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────┐│
│  │ FFmpeg   │  │ ASR      │  │ ComfyUI  │  │ Seedream         ││
│  │ 音视频   │  │ 语音识别 │  │ 图像处理 │  │ 文生图           ││
│  │ 处理集群 │  │ (豆包)   │  │ (背景移除)│  │ (素材补全)      ││
│  └──────────┘  └──────────┘  └──────────┘  └──────────────────┘│
│  ┌──────────┐  ┌──────────────────────────────────┐             │
│  │ Remotion │  │ Strategy Router                   │             │
│  │ 视频渲染 │  │ (TrimAndCut / SmartCrop /         │             │
│  │ (:3001)  │  │  KenBurns / AudioDucking / ...)   │             │
│  └──────────┘  └──────────────────────────────────┘             │
└─────────────────────────────────────────────────────────────────┘
          │
┌─────────▼───────────────────────────────────────────────────────┐
│                   数据与知识底座                                  │
│                                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────┐│
│  │ MySQL    │  │ Redis    │  │ ChromaDB │  │ 本地文件系统     ││
│  │ 关系数据 │  │ 缓存/    │  │ 向量存储 │  │ storage/         ││
│  │          │  │ 渲染进度 │  │          │  │                  ││
│  └──────────┘  └──────────┘  └──────────┘  └──────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 核心数据流

系统的完整业务闭环由两条主链路构成：

#### 链路一：拆解链路（Deconstruct）

用户上传参考视频后，系统自动完成从原始视频到结构化模板的全流程处理。

```
用户上传视频
  │
  ├─→ FFprobe 探测元数据（分辨率、时长、编码格式）
  │
  ├─→ [异步] ASR 语音识别
  │     └─ FFmpeg 抽取音轨 → 豆包 Seed ASR 模型转写
  │         → 逐句时间轴 + 语义标签（情绪/音量/BGM 流派）
  │
  ├─→ [异步] Scene 镜头检测
  │     └─ FFmpeg scene filter (高敏阈值 0.1)
  │         → 触发 KeyFrame 关键帧抽取
  │              └─ Hook-Locking 策略选点
  │
  └─→ [阶段完成后] Timeline 多路时间轴归一
        └─ ASR + Scene + KeyFrame 三路对齐 → LLM 结构化分析
            → 品类知识热注册 → ChromaDB 向量同步
```

#### 链路二：创作链路（Creation）

基于拆解产出的结构模板，自动完成从素材匹配到成片渲染。

```
选取模板快照 + 上传新素材
  │
  ├─→ 素材多模态理解（AssetProfiler）
  ├─→ 槽位匹配（SlotMatcher）→ 缺口素材 Seedream 文生图补全
  ├─→ 素材适配编排（AdaptationOrchestrator）→ FFmpeg 策略链执行
  ├─→ ComfyUI 背景移除（按需）
  ├─→ BGM 推荐与匹配（ChromaDB 向量检索）
  └─→ Remotion 渲染 → H.264 MP4 成片
```

### 2.3 品类知识自进化机制与 RAG 检索

系统并不是硬编码死板的视频模板，而是具备基于大模型分析结果的**品类知识自进化（Self-Evolution）**能力。当系统处理越来越多的视频时，它会自动归纳新品类特征，并将其沉淀到关系型数据库（MySQL）与向量检索库（ChromaDB）中，使得系统"越用越聪明"。

#### 机制触发点与全链路图解

```text
┌─────────────────────────────────────────────────────────────────┐
│                       ① 上传参考视频 (User)                      │
└──────────────────────────────┬──────────────────────────────────┘
                               │ 触发视频拆解任务 (包含 ASR/Scene/关键帧)
┌──────────────────────────────▼──────────────────────────────────┐
│               ② RAG 检索阶段 (ChromaDB VectorStore)              │
│  提取当前视频的时序语义向量 (如画面波形、ASR摘要)                  │
│  检索并返回 Top-K 相似品类上下文 (Category Knowledge)             │
└──────────────────────────────┬──────────────────────────────────┘
                               │ 注入 Prompt: 多模态时序日志 + Top-K 品类上下文
┌──────────────────────────────▼──────────────────────────────────┐
│               ③ 结构分析与动态测算 (豆包大模型 LLM)               │
│  【动态特征自进化契约】                                           │
│   - Inherit (刚性继承): 严格沿用已知品类上下文中的 Schema 字段    │
│   - Evaluate(动态测算): 依据当前视频计算出具体的 fieldValue       │
│   - Insert  (盲区发现): 发现并新增全新 Schema 字段 (上限5个)      │
└──────────────────────────────┬──────────────────────────────────┘
                               │ 返回 video-structure-template JSON
┌──────────────────────────────▼──────────────────────────────────┐
│               ④ 后置清洗与自进化入库 (Analyzer)                   │
│  【数据清洗防线】强制规范校验、拦截过拟合泄露词、熔断异常膨胀       │
│                                                                 │
│  ┌────────────────────────┐         ┌────────────────────────┐  │
│  │    判定为【已知品类】     │         │    判定为【新品类】      │  │
│  │ 更新/扩展 Schema (MySQL) │   or    │ 热注册新品类Schema(MySQL)│  │
│  │ 更新 Embedding (ChromaDB)│         │ 生成全新 Embedding 并入库│  │
│  └────────────────────────┘         └────────────────────────┘  │
└──────────────────────────────┬──────────────────────────────────┘
                               │ 
                               ▼ 拆解完成，沉淀为具备更强适应性的结构化模板
```

#### 动态特征自进化契约详解

1. **刚性继承（Inherit）**：RAG 检索到的特征 Schema 必须严格遵守，作为分析的基础维度。
2. **动态测算（Evaluate）**：LLM 依据**当前**视频，为继承的字段计算出具体的 `fieldValue`，但**不会覆盖回数据库的 Schema 定义中**。
3. **盲区发现（Insert）**：如果 LLM 发现了现有知识库中未记载的显著结构特征，它可以主动新增特征字段。后端清洗后，仅将新特征的 Schema 追加合并到 MySQL 的 `dynamic_fields` 数组中。
4. **格式约束**：所有动态特征都必须以 `{ fieldName, fieldType, fieldValue, description }` 的标准 JSON 结构输出。

#### 数据清洗与保护防线

- **规范强校验**：强制校验字段名命名规范（必须为 `snake_case`）和数据类型（仅限 `STRING` / `DOUBLE` / `INTEGER` / `BOOLEAN` / `JSON`）。
- **过拟合拦截**：启发式泄漏词表检测，防止大模型将特定视频的偶发细节当作品类共有特征沉淀。
- **膨胀熔断机制**：强制执行单次新增字段的上限截断，检测到无意义的批量字段编造时直接熔断。

---

## 三、工具协议

### 3.1 LLM 交互机制

系统通过 `ArkClient` 与字节跳动方舟（Ark）大模型 API 交互：

- **通信方式**：标准 HTTP POST，`Authorization: Bearer <API_KEY>`
- **请求格式**：OpenAI 兼容的 Chat Completions 协议
- **模型端点**：通过 `ARK_MODEL_ENDPOINT` 环境变量配置
- **超时控制**：`ARK_TIMEOUT_SECONDS`，默认 200 秒

### 3.2 核心 JSON 协议总览

系统中所有 Prompt 模板集中维护在 `ArkPromptTemplates.java`。LLM 的输入和输出均受到严格的 JSON Schema 约束。以下按业务链路顺序展示四大核心协议的 JSON 结构。

---

#### 3.2.1 ASR 语音识别 JSON（`ASR_TRANSCRIBE_JSON`）

音频被 FFmpeg 抽取后送入豆包 Seed ASR 模型，模型需按以下协议输出结构化结果。该 JSON 同时承载了**语音转写**和**音频语义标注**两个任务，是后续 Timeline 对齐的关键输入源。

```json
{
  "fullText": "完整转写文本",
  "segments": [
    {
      "segmentIndex": 0,
      "start": 0.0,
      "end": 3.5,
      "text": "大家好，欢迎来到今天的视频",
      "speaker": "SPEAKER_1",
      "confidence": 0.97,
      "audioEmotion": "CALM",
      "volumeIntensity": "MEDIUM",
      "backgroundEnvironment": "MUSIC",
      "vocalVibe": "平稳自然的解说语气",
      "bgmGenre": "轻电子流行",
      "bgmInstruments": "合成器pad与轻打击"
    }
  ]
}
```

**segments[] 关键字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `audioEmotion` | enum | 语音情绪。`CALM` / `CURIOUS` / `AGITATED` / `EXCITED` / `URGENT` / `NONE` |
| `volumeIntensity` | enum | 音量烈度。`LOW`=窃窃私语、`MEDIUM`=常规交谈、`HIGH`=喊叫、`PEAK`=咆哮 |
| `backgroundEnvironment` | enum | 背景声环境。`MUSIC` / `FX` / `MUSIC_FX` / `NOISE` / `SILENT` |
| `vocalVibe` | string | 自然语言描述说话人的情绪张力 |
| `bgmGenre` | string | 背景音乐流派 |

---

#### 3.2.2 视频结构模板 JSON（`video-structure-template/v2`）

这是系统中最核心的 LLM 输出协议。`StructureAnalyzerServiceImpl` 将多模态时序剧本（ASR + Scene + KeyFrame 三路对齐后的 Markdown）与关键帧 Base64 图片一并送入 LLM，要求输出符合以下 Schema 的 JSON。

```json
{
  "$schema": "video-structure-template/v2",
  "templateId": "UUID",
  "templateName": "模板名称",
  "version": "1.0.0",
  "category": "short_drama",
  "meta": {
    "targetDuration": { "min": 25, "max": 35, "unit": "seconds" },
    "aspectRatio": "9:16",
    "resolution": { "width": 1080, "height": 1920 },
    "acousticEnvironment": "speech_focused",
    "styles": ["风格标签1", "风格标签2"],
    "description": "模板特征概述"
  },
  "scriptStructure": {
    "totalSegments": 4,
    "segments": [
      {
        "segmentIndex": 0,
        "role": "hook",
        "label": "段落名称",
        "description": "段落功能描述",
        "durationRange": { "min": 2, "max": 4 },
        "durationWeight": 0.12,
        "scriptHint": "文案提示",
        "requiredElements": [],
        "shotCount": { "min": 1, "max": 2 },
        "preferredShotTypes": ["CLOSE_UP"],
        "preferredCameraMovements": ["STATIC", "ZOOM_IN"],
        "requiredVisualFunctions": ["subject_intro"],
        "subtitleStrategy": "guided",
        "packagingDensity": "medium",
        "fallbackStrategies": ["text_overlay_replace"]
      }
    ]
  },
  "rhythmStructure": {
    "overallPace": "medium",
    "avgShotDuration": 2.8,
    "paceCurve": [{ "timePercent": 0, "pace": "fast", "note": "说明" }],
    "climaxPositions": [{ "startPercent": 65, "endPercent": 80 }],
    "transitionStyles": ["hard_cut_dominant"],
    "beatSyncPoints": [{ "timePercent": 0, "type": "visual_hit" }]
  },
  "packagingStructure": {
    "subtitleStyles": [{ "position": "bottom_center", "fontSize": "medium", "color": "#FFFFFF", "background": "semi_transparent_black", "animation": "fade_in" }],
    "titleCards": [],
    "transitions": [],
    "coverStyles": [{ "layout": "text_left_image_right", "textElements": ["主标题"], "colorTone": "#FF8C00" }]
  },
  "shots": [
    {
      "shotIndex": 0, "belongsToSegment": 0,
      "shotType": "face_closeup", "shotTypeTag": "CLOSE_UP",
      "functionHint": "subject_intro",
      "durationRange": { "min": 2, "max": 3 },
      "cameraMovement": "static", "cameraMovementTag": "STATIC",
      "requiredContent": []
    }
  ],
  "categoryExtensions": {
    "discoveredCategoryId": "short_drama",
    "discoveredCategoryName": "短剧",
    "dynamicExtensionFields": [
      { "fieldName": "cut_frequency", "fieldType": "STRING", "fieldValue": "low_frequency", "description": "长镜头慢节奏为主" },
      { "fieldName": "motion_effects", "fieldType": "JSON", "fieldValue": ["slow_motion"], "description": "该品类常用的镜头运动与特效列表", "allowedValues": ["speed_ramp", "zoom_punch", "shake", "slow_motion"] }
    ],
    "discoveredPromptOverrides": {}
  },
  "viralFactors": [
    { "factorName": "情绪共鸣", "weight": 0.8, "description": "分析该视频能爆火的原因" }
  ]
}
```

**关键约束摘要：**
- `segments[].role` 枚举：`hook` | `body` | `climax` | `outro`
- `overallPace` / `paceCurve[].pace` 枚举：`slow` | `medium` | `fast` | `very_fast` | `ultra_fast`
- `beatSyncPoints[].type` 枚举：`visual_hit` | `audio_hit` | `climax_hit`
- `acousticEnvironment` 枚举：`asmr` | `quiet` | `noisy` | `speech_focused` | `music_driven`
- `shotTypeTag` 枚举：`CLOSE_UP` | `MID_SHOT` | `WIDE_SHOT`
- `cameraMovementTag` 枚举：`STATIC` | `ZOOM_IN` | `ZOOM_OUT` | `PAN`
- `dynamicExtensionFields` 的 `fieldType` 仅允许：`STRING` | `DOUBLE` | `INTEGER` | `BOOLEAN` | `JSON`

---

#### 3.2.3 创作槽位匹配与素材缺口补全 JSON（`slot-match-result/v1`）

该 JSON 是模板结构与用户输入素材进行匹配（Slot Match）的结果。其核心除了建立槽位到资产的映射外，还必须承担**素材缺口补全**（AI 生图补位）的智能决策。

```json
{
  "$schema": "slot-match-result/v1",
  "projectId": "crp_xxx",
  "versionId": "ver_xxx",
  "overallCoverage": 0.75,
  "gapSummary": "outro 缺少品牌LOGO素材，已建议AI生图补位",
  "matchDecisions": [
    {
      "segmentIndex": 0,
      "segmentRole": "hook",
      "matchedAssetId": "2050000000000000000",
      "matchScore": 0.86,
      "matchStatus": "MATCHED",
      "matchReason": "素材高光覆盖开头产品静物展示",
      "adaptationPlan": {
        "strategyChain": [
          { "strategyType": "TRIM_AND_CUT", "params": { "startTime": 0.0, "endTime": 4.0, "speed": 1.0 } },
          { "strategyType": "SMART_CROP", "params": { "targetWidth": 1080, "targetHeight": 1920 } }
        ]
      }
    },
    {
      "segmentIndex": 3,
      "segmentRole": "outro",
      "matchStatus": "MISSING",
      "vetoReason": "outro缺少品牌LOGO收尾素材",
      "imageGenEligible": true,
      "imageGenCategory": "LOGO",
      "imageGenPrompt": "A modern minimalist e-commerce brand logo...",
      "imageGenDescription": "现代极简电商品牌LOGO，购物车图标搭配白金色渐变"
    }
  ]
}
```

**matchStatus 枚举**：`MATCHED` | `PARTIAL` | `MISSING` | `VETOED`
**imageGenCategory 枚举**：`UI_ELEMENT` | `STICKER` | `LOGO` | `ILLUSTRATION` | `BACKGROUND` | `NONE`

---

#### 3.2.4 Remotion 渲染编排 JSON（`composition-script/v1`）

创作链路的最终产物。后端将模板导演说明、已适配素材 URL 和 BGM 信息注入 Prompt，由 LLM 输出 `CompositionScript` JSON，通过 `RemotionServiceClient` POST 至渲染服务。

```json
{
  "$schema": "composition-script/v1",
  "projectId": "crp_xxx",
  "canvas": { "width": 1080, "height": 1920, "fps": 30 },
  "globalStyle": { "fontFamily": "Noto Sans SC", "fontTier": "subtitle", "backgroundColor": "#000000" },
  "bgm": {
    "src": "http://localhost:8080/api/storage/audio-database/demo.mp3",
    "mixLevel": "BALANCED",
    "loop": true,
    "fadeInFrames": 15,
    "fadeOutFrames": 30
  },
  "scenes": [
    {
      "sceneId": "scene_0",
      "sceneIndex": 0,
      "role": "hook",
      "durationInFrames": 120,
      "layers": [
        {
          "layerId": "layer_0_bg",
          "preset": "bg.mesh_gradient",
          "enterAtFrame": 0,
          "durationInFrames": 120,
          "params": { "colors": ["#0F2027", "#203A43", "#7C3AED"], "intensity": 0.92 }
        },
        {
          "layerId": "layer_0_title",
          "preset": "text.kinetic_pop",
          "enterAtFrame": 8,
          "durationInFrames": 32,
          "params": {
            "text": "标题文本",
            "fontSize": 118,
            "color": "#FFFFFF",
            "positionPreset": "hero_center",
            "scaleFrom": 1.9,
            "enterFrames": 14
          }
        },
        {
          "layerId": "layer_0_video",
          "preset": "media.video",
          "enterAtFrame": 0,
          "durationInFrames": 120,
          "params": { "src": "http://localhost:8080/api/storage/creation-adapt/crp_xxx/ver_xxx/adapted_seg_0.mp4", "volume": 0.8, "muted": false }
        }
      ]
    }
  ],
  "transitions": [
    {
      "fromSceneIndex": 0,
      "toSceneIndex": 1,
      "preset": "transition.fade",
      "params": { "durationInFrames": 15, "timing": "linear" }
    }
  ]
}
```

**字体分层系统 (Font Tier)：**

| Tier | 加载字体 | 适用场景 | 默认组件 |
|------|---------|---------|---------| 
| `title` | ZCOOL XiaoWei | 标题/Hero 大字 | `text.hero_billboard`, `text.fade_title` |
| `subtitle` | Noto Sans SC | 字幕/正文（高可读） | `text.mask_reveal`, `text.word_highlight`, `caption.subtitle` |
| `accent` | Ma Shan Zheng | 强调爆点（书法风） | `text.kinetic_pop` |
| `ui` | Inter | 标签/角标（现代风） | `text.label_chip`, `overlay.badge_pop` |
| `number` | Bebas Neue | 数字滚动（展示体） | `text.counter_number` |
| `bodySerif` | Noto Serif SC | 衬线正文（文学感） | `text.typewriter` |
| `kaiStyle` | LXGW WenKai TC | 楷体引用 | — |

**关键枚举约束摘要：**
- `bgm.mixLevel`：`QUIET` | `BALANCED` | `DRIVE`
- `positionPreset`：`hero_top` | `hero_center` | `hero_lower` | `left_focus` | `right_focus`
- `transition.params.timing`：`linear` | `spring`

---

#### 3.2.5 BGM 配乐分析 JSON（`BGM_ANALYZER_JSON`）

该协议用于对存入本地库的 BGM 音乐进行多模态听觉分析。生成的 JSON 会存入 ChromaDB 向量库，为后续自动配乐和音频时序切片提供检索引擎支持。

```json
{
  "audioName": "未来幻象",
  "globalMetrics": {
    "bpm": 120,
    "overallStyle": "ambient_synthwave",
    "durationSeconds": 180
  },
  "loudnessMetrics": {
    "integratedLufs": -14.2,
    "truePeakDbtp": -1.0,
    "loudnessRangeLra": 5.4
  },
  "auditoryTimeline": [
    {
      "timeRange": { "start": 0, "end": 15 },
      "auditoryPerception": { 
        "moodVibe": "神秘酝酿", 
        "energyLevel": "low", 
        "acousticFeatures": ["模拟合成器"] 
      },
      "bestMatchedSlot": "hook",
      "recommendedMaterial": "星空、夜景、缓慢推移"
    }
  ],
  "mixDefaults": {
    "defaultMixLevel": "BALANCED",
    "fadeInFrames": 15,
    "fadeOutFrames": 30,
    "duckingRatio": 0.5,
    "duckingRecommended": true,
    "loopRecommended": true
  }
}
```

**关键约束说明：**
- `energyLevel` 枚举：`low` / `medium` / `high` / `peak`
- `bestMatchedSlot` 角色：`hook` / `body` / `climax` / `outro`
- `mixDefaults.defaultMixLevel`：`QUIET` / `BALANCED` / `DRIVE`
- `loudnessMetrics` 为物理响度预估，用于动态音频闪避计算。

> 各预设（小协议）的详细参数（`params`）字段约束请参见本文末尾的 [附录 E](#附录-eremotion-预设组件参数白名单-layerparams)。

### 3.3 素材适配策略路由

| 策略类型 | 执行器 | 功能 |
|----------|--------|------|
| `TRIM_AND_CUT` | TrimAndCutExecutor | 按时间戳裁切视频片段 |
| `SMART_CROP` | SmartCropExecutor | 智能裁切画面区域 |
| `KEN_BURNS_MOTION` | KenBurnsMotionExecutor | 静态图片缩放平移动效 |
| `LIGHTING_ADJUST` | LightingAdjustExecutor | 亮度/对比度调整 |
| `LOCAL_BLUR` | LocalBlurExecutor | 局部模糊处理 |
| `LOOP_SEQUENCE` | LoopSequenceExecutor | 短素材循环扩展 |

### 3.4 Remotion 渲染预设清单

| 分类 | preset ID | 说明 |
|------|-----------|------|
| 背景 | `bg.mesh_gradient` / `bg.tech_grid` / `bg.noise_grain` | 渐变网格 / 科技网格 / 噪点纹理 |
| 媒体 | `media.video` / `media.image` / `media.audio` | 视频 / 图片 / 音效层 |
| 运动 | `motion.ken_burns` / `motion.float_2d5` / `motion.parallax_drift` / `motion.perspective_tilt` | Ken Burns / 2.5D悬浮 / 视差漂移 / 透视倾斜 |
| 文本 | `text.fade_title` / `text.kinetic_pop` / `text.hero_billboard` / `text.typewriter` / `text.mask_reveal` / `text.word_highlight` / `text.counter_number` / `text.label_chip` | 各类文字动效组件 |
| 字幕 | `caption.subtitle` | 底部/顶部字幕 |
| 叠加 | `overlay.light_leak` / `overlay.flash` / `overlay.badge_pop` / `overlay.glow_frame` | 光晕 / 闪白 / 徽章弹出 / 发光边框 |
| 衬底 | `backing.solid_plate` / `backing.capsule` / `backing.glass_plate` | 实色板 / 胶囊衬底 / 毛玻璃板 |
| 转场 | `transition.fade` / `transition.slide` / `transition.wipe` | 淡入淡出 / 滑动 / 擦除 |

---

## 四、安全边界

### 4.1 API 密钥管理

所有外部服务密钥均通过环境变量注入，不存在源码硬编码：

| 密钥 | 环境变量 | 用途 |
|------|----------|------|
| 方舟 LLM API Key | `ARK_API_KEY` | 大模型调用鉴权 |
| Seedream API Key | `SEEDREAM_API_KEY` | 文生图服务鉴权 |
| ChromaDB Key Token | `CHROMA_KEY_TOKEN` | 向量数据库鉴权（可选） |
| MySQL 密码 | `DB_PASSWORD` | 数据库连接 |
| Redis 密码 | `REDIS_PASSWORD` | 缓存服务连接 |

### 4.2 输入校验

- **文件上传**：限定后缀名为 `mp4`/`mov`，文件大小上限 2GB。
- **LLM 输出清洗**：结构分析结果经过多层清洗（品类名称白名单、字段名正则校验、泄漏关键词过滤）。
- **FFmpeg 命令审计**：所有命令执行前完整记录到 `creation_ffmpeg_command_log` 表，含退出码、耗时、输出尾部。

### 4.3 受保护资产机制

`AdaptationOrchestrator` 内置受保护视觉资产检测。当素材包含 `logo`、`brand_logo`、`icon`、`sticker` 等标识时，系统跳过 FFmpeg 处理，直接复制原始文件，防止品牌素材被裁切或变形。

### 4.4 服务隔离

- 后端与 Remotion 渲染服务通过 HTTP 调用解耦，渲染服务崩溃不影响后端主服务。
- ComfyUI / Ollama 运行在宿主机上，服务不可用时对应功能降级但不阻塞主流程。
- 拆解链路的 ASR、Scene、KeyFrame 三个阶段通过 `VideoTaskStageService` 独立管理状态，单个阶段失败支持独立重试。

---

## 五、AI 辅助开发工具使用报告

项目采用了**"人工主导架构设计与决策，AI 辅助代码落地与敏捷迭代"**的研发理念。

### 5.1 AI 工具栈

1. **Google Gemini (包含 Antigravity IDE)**
2. **Anthropic Claude Code**
3. **OpenAI Codex**
4. **DeepSeek**

### 5.2 人机协同边界

| 环节 | 人工主导 | AI 辅助 |
|------|----------|---------|
| 产品原型与前端 | UI 布局、交互流程手绘设计 | React 组件编写、CSS 样式还原 (Gemini) |
| 系统架构 | "拆解→重组→自进化"宏观执行顺序，Spring Boot 模块划分 | 技术可行性评审、工程难点指出 (Claude) |
| Prompt 工程 | 大协议拆解方向、字段层级关系 | 字段穷举发散、参数白名单完善 (多模型) |
| 业务逻辑 | 黑盒/白盒测试、边界异常处理、联调 | 具体业务类实现、FFmpeg 执行器、CRUD (Codex/Gemini/DeepSeek) |

> **核心总结**："大脑"始终是人工开发者。AI 辅助工具作为极致高效的"手"和"外脑验证器"，接管了繁重的机械代码编写与方案穷举验证。

---

## 六、代码运行说明

### 6.1 环境依赖清单

| 依赖项 | 最低版本 | 用途 | 必选/可选 |
|--------|----------|------|-----------|
| **JDK** | 17+ | 后端 Spring Boot 编译运行 | 必选 |
| **Maven** | 3.8+ | 后端构建工具 | 必选 |
| **Node.js** | 18+ | 前端 + Remotion 渲染服务 | 必选 |
| **npm** | 8+ | Node.js 包管理器（随 Node.js 附带） | 必选 |
| **FFmpeg** | 5+ | 音视频处理核心引擎 | 必选 |
| **Docker** | 20+ | 运行 MySQL / Redis / ChromaDB 容器 | 必选 |
| **Docker Compose** | v2+ | 容器编排 | 必选 |
| **Ollama** | — | 向量嵌入模型（nomic-embed-text） | 可选（拆解链路向量同步需要） |
| **ComfyUI** | — | AI 背景移除 | 可选（抠图功能需要） |

### 6.2 必备配置准备（环境变量）

在任何启动方案之前，**必须**先配置后端的大模型 API 密钥。

1. 进入 `backend` 目录，找到 `.env.template` 文件。
2. 复制该文件并重命名为 `.env`。
3. 编辑 `.env` 文件，填入火山引擎（字节方舟）的 API 凭证：

| 变量名 | 必填说明 |
|--------|------|
| `ARK_API_KEY` | 必填，字节方舟大模型 API Key |
| `ARK_MODEL_ENDPOINT` | 必填，方舟模型端点 ID（推荐使用 doubao-pro 等高级模型） |
| `SEEDREAM_API_KEY` | 选填，用于素材缺口补全（文生图） |

> **提示：** 如果你是通过 Windows 一键脚本（`start.ps1`）启动，脚本会在首次运行时自动为你生成 `.env` 文件并暂停，等待你填写。

### 6.3 方案一：Windows 一键启动（推荐）

项目根目录提供了经过全面测试的 PowerShell 一键启动脚本：

```powershell
# 启动（推荐直接在 pwsh / PowerShell 7+ 中执行）
pwsh -File .\start.ps1

# 停止
pwsh -File .\stop.ps1
```

**脚本自动执行以下全部流程：**
1. ✅ 环境检查（Java / Maven / Node.js / FFmpeg / Docker）
2. ✅ 探测可选服务（ComfyUI / Ollama）并给出提示
3. ✅ 首次运行时自动从 `.env.template` 生成配置文件，并中断提示用户填入 API Key
4. ✅ 启动基础设施容器（MySQL / Redis / ChromaDB）并等待预热
5. ✅ 自动安装依赖（`npm install`，仅首次）
6. ✅ 分别在最小化的后台窗口中启动 Backend / Frontend / Remotion Service

> **注意**：脚本现已统一切换到 PowerShell 7+ (`pwsh.exe`) 运行；如果误从 Windows PowerShell 5.1 打开，脚本会自动重启到 `pwsh`。请先确保本机已安装 `pwsh.exe`。

### 6.4 方案二：手动分步启动（跨平台兜底方案）

适用于 macOS / Linux 或 Windows 上无法运行 PowerShell 脚本的场景。

#### 步骤 1：启动基础设施

```bash
# 在项目根目录执行
docker-compose up -d mysql redis chroma

# 等待数据库初始化完成（约 15 秒）
sleep 15
```

#### 步骤 2：配置环境变量

```bash
# 后端：复制模板并填入 API Key
cp backend/.env.template backend/.env
# 编辑 backend/.env，填入 ARK_API_KEY、ARK_MODEL_ENDPOINT 等

# 前端（如需要）
cp frontend/.env.local.example frontend/.env.local
```

**后端 `.env` 需要填写的关键变量：**

| 变量名 | 说明 |
|--------|------|
| `ARK_API_KEY` | 字节方舟大模型 API Key |
| `ARK_MODEL_ENDPOINT` | 方舟模型端点 ID |
| `SEEDREAM_API_KEY` | Seedream 文生图 API Key（可选） |

#### 步骤 3：启动 Remotion 渲染服务

```bash
cd remotion-service
npm install        # 仅首次
npm run server     # 监听 :3001
```

#### 步骤 4：启动前端开发服务器

```bash
cd frontend
npm install        # 仅首次
npm run dev        # 监听 :5173
```

#### 步骤 5：启动后端 Spring Boot

```bash
cd backend
mvn spring-boot:run
# 或使用 IDE (IntelliJ IDEA) 直接运行 Application.java
```

#### 步骤 6：验证服务

| 服务 | 地址 |
|------|------|
| 前端页面 | http://localhost:5173 |
| 后端 API | http://localhost:8080 |
| Remotion 渲染服务 | http://localhost:3001 |
| MySQL | localhost:3306 (root / root) |
| Redis | localhost:6379 |
| ChromaDB | localhost:8000 |

### 6.5 方案三：Docker Compose 全容器化部署

适用于生产环境或不想在宿主机安装任何开发依赖的场景：

```bash
# 一次性构建并启动所有服务
docker-compose up -d --build

# 前端访问地址变为 http://localhost:80
# 后端 / Remotion 端口映射不变
```

> **注意**：全容器化部署时，ComfyUI 和 Ollama 需在宿主机上运行，容器通过 `host.docker.internal` 访问。

### 6.6 常见问题与排错

| 问题现象 | 可能原因 | 解决方案 |
|----------|----------|----------|
| `start.ps1` 报错 "无法加载文件" | PowerShell 执行策略限制 | 以管理员身份运行 `Set-ExecutionPolicy RemoteSigned` |
| Maven 构建失败 | JDK 版本不符 | 确认 `java -version` 输出为 17+ |
| Docker 容器启动失败 | Docker Desktop 未运行 | 启动 Docker Desktop 并等待引擎就绪 |
| 端口 3306/6379 被占用 | 本地已有 MySQL/Redis 实例 | 停止本地实例或修改 `docker-compose.yml` 端口映射 |
| Remotion 渲染超时 | 首次渲染需下载 Chromium | 等待下载完成，后续渲染速度会显著提升 |
| 拆解报错"向量同步失败" | Ollama 未启动 | 启动 Ollama 并执行 `ollama pull nomic-embed-text` |

---

## 七、项目目录结构

```
bytedance-ai-video/
├── backend/                    # Java Spring Boot 后端
│   ├── src/main/java/          # 业务源码
│   ├── src/main/resources/     # 配置文件 + Flyway 迁移脚本
│   ├── .env.template           # 环境变量模板
│   └── pom.xml
├── frontend/                   # React + Vite 前端
│   ├── src/pages/              # 页面组件
│   ├── src/components/         # 公共组件
│   └── package.json
├── remotion-service/           # Remotion 视频渲染服务
│   ├── src/                    # 渲染组件 + HTTP Server
│   └── package.json
├── storage/                    # 本地文件存储
│   ├── analysis-video/         # 上传的参考视频
│   ├── analysis-audio/         # ASR 抽取的音频
│   ├── audio-database/         # BGM 音乐库
│   └── creation-material/      # 创作素材
├── docs/                       # 技术文档
│   ├── architecture.md         # 完整技术架构文档（含附录）
│   ├── ai_tools_usage.md       # AI 工具使用报告
│   └── SUBMISSION.md           # 本交付物文档
├── docker-compose.yml          # 容器编排配置
├── start.ps1                   # Windows 一键启动脚本
├── stop.ps1                    # Windows 一键停止脚本
└── README.md
```

---

*文档生成时间：2026-06-09*

## 附录 A：ASR JSON 字段说明

[← 返回正文 3.2.1 节](#321-asr-语音识别-jsonasr_transcribe_json)

### 根级字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `fullText` | string | 整段音频的完整转写文本拼接 |
| `segments` | array | 按时间升序排列的逐句切分数组 |

### segments[] 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `segmentIndex` | integer | 句段序号（从 0 开始） |
| `start` | number | 句段起始时间（秒） |
| `end` | number | 句段结束时间（秒），必须大于 start |
| `text` | string | 该句段的转写文本，无人声时为空字符串 |
| `speaker` | string | 说话人标识。推荐值 `SPEAKER_1`/`SPEAKER_2`，无法区分时填 `UNKNOWN` |
| `confidence` | number | 识别置信度，取值范围 `[0, 1]` |
| `audioEmotion` | enum | 语音情绪。枚举：`CALM` / `CURIOUS` / `AGITATED` / `EXCITED` / `URGENT` / `NONE` |
| `volumeIntensity` | enum | 音量烈度（物理锚定）。`LOW`=窃窃私语、`MEDIUM`=常规交谈、`HIGH`=明显提高音量的喊叫、`PEAK`=情绪失控的咆哮 |
| `backgroundEnvironment` | enum | 背景声环境。`MUSIC`=背景音乐为主、`FX`=特殊音效为主、`MUSIC_FX`=音乐与音效同时存在、`NOISE`=环境噪声、`SILENT`=近静音 |
| `vocalVibe` | string | 自然语言描述说话人的情绪张力（如"压抑克制的低语"）；无人声时为空字符串 |
| `bgmGenre` | string | 背景音乐流派（如"悬疑底噪"）；无音乐时为空字符串 |
| `bgmInstruments` | string | 最突出的乐器组合（如"低频合成器与弱打击乐"）；无音乐时为空字符串 |

---

<a id="appendix-b"></a>
## 附录 B：video-structure-template JSON 字段说明

[← 返回正文 3.2.2 节](#322-视频结构模板-jsonvideo-structure-templatev2)

### 根级字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `$schema` | string | 固定为 `video-structure-template/v2` |
| `templateId` | string | 自动生成的 UUID |
| `templateName` | string | 模板的高概括性名称 |
| `version` | string | 语义化版本号 |
| `category` | string | 品类 ID。已知品类填对应 ID，新品类使用宏观行业分类的 snake_case 命名 |

### meta 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| `targetDuration` | object | 目标时长范围，含 `min`/`max`（秒）和 `unit` |
| `aspectRatio` | string | 画面比例，如 `9:16` |
| `resolution` | object | 分辨率，含 `width`/`height` |
| `acousticEnvironment` | enum | 声学环境。枚举：`asmr` / `quiet` / `noisy` / `speech_focused` / `music_driven`。用于后续音频匹配的物理环境 Veto 校验 |
| `styles` | array | LLM 自动归纳的风格描述标签 |
| `description` | string | 模板来源与特征概述 |

### scriptStructure.segments[] 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `segmentIndex` | integer | 段落序号 |
| `role` | enum | 段落角色。仅允许 `hook` / `body` / `climax` / `outro` |
| `label` | string | 段落名称 |
| `description` | string | 段落功能描述 |
| `durationRange` | object | 时长范围（秒），含 `min`/`max` |
| `durationWeight` | number | 该段占总时长的权重，`[0.0, 1.0]` |
| `scriptHint` | string | 文案创作提示 |
| `requiredElements` | array | 必须包含的元素列表 |
| `shotCount` | object | 镜头数量范围，含 `min`/`max` |
| `preferredShotTypes` | array | 推荐机位。枚举：`CLOSE_UP` / `MID_SHOT` / `WIDE_SHOT` / `UNKNOWN` |
| `preferredCameraMovements` | array | 推荐运镜。枚举：`STATIC` / `ZOOM_IN` / `ZOOM_OUT` / `PAN` / `UNKNOWN` |
| `requiredVisualFunctions` | array | 视觉功能标签。推荐值：`subject_intro` / `detail_showcase` / `usage_process` / `emotion_push` / `proof_or_comparison` / `benefit_recall` / `cta_prompt` / `static_summary_card` |
| `subtitleStrategy` | enum | 字幕策略。枚举：`none` / `sparse` / `guided` / `dense` |
| `packagingDensity` | enum | 包装密度。枚举：`none` / `low` / `medium` / `high` |
| `fallbackStrategies` | array | 降级策略。推荐值：`reorder_structure` / `text_overlay_replace` / `packaging_emphasis` / `reuse_existing_material` / `static_summary_card` |

### rhythmStructure 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| `overallPace` | enum | 整体节奏等级。枚举：`slow` / `medium` / `fast` / `very_fast` / `ultra_fast` |
| `avgShotDuration` | number | 平均镜头时长（秒） |
| `paceCurve[]` | array | 节奏曲线切片。每项含 `timePercent`(0-100)、`pace`(枚举)、`note` |
| `climaxPositions[]` | array | 高潮位置。每项含 `startPercent`/`endPercent` |
| `transitionStyles` | array | 转场风格标签 |
| `beatSyncPoints[]` | array | 卡点。每项含 `timePercent`、`type`（枚举：`visual_hit` / `audio_hit` / `climax_hit`） |

### shots[] 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `shotIndex` | integer | 镜头序号 |
| `belongsToSegment` | integer | 所属段落的 segmentIndex |
| `shotType` | string | 物理视角描述（如 `face_closeup`/`first_person`/`top_down`） |
| `shotTypeTag` | enum | 刚性标签。枚举：`CLOSE_UP` / `MID_SHOT` / `WIDE_SHOT` |
| `functionHint` | string | 功能语义标签 |
| `durationRange` | object | 时长范围（秒） |
| `cameraMovement` | string | 运镜描述（自然语言） |
| `cameraMovementTag` | enum | 运镜标签。枚举：`STATIC` / `ZOOM_IN` / `ZOOM_OUT` / `PAN` |

### categoryExtensions 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| `discoveredCategoryId` | string | 品类 ID（与根级 `category` 一致） |
| `discoveredCategoryName` | string | 品类中文名 |
| `dynamicExtensionFields[]` | array | 品类动态特征数组。每项含 `fieldName`(snake_case)、`fieldType`(枚举)、`fieldValue`、`description`、可选 `allowedValues` |
| `discoveredPromptOverrides` | object | 品类专用 Prompt 覆写（可选） |

---

<a id="appendix-c"></a>
## 附录 C：slot-match-result JSON 字段说明

[← 返回正文 3.2.3 节](#323-创作槽位匹配与素材缺口补全-jsonslot-match-resultv1)

### 根级字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `$schema` | string | 固定为 `slot-match-result/v1` |
| `projectId` | string | 项目 ID |
| `versionId` | string | 匹配结果的版本标识 |
| `overallCoverage` | number | 整体素材覆盖率，`[0.0, 1.0]` |
| `gapSummary` | string | 对本次匹配结果的自然语言摘要，特别是缺口情况说明 |
| `matchDecisions` | array | 具体到每个 segment 的匹配决定 |

### matchDecisions[] 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `segmentIndex` | integer | 段落序号 |
| `segmentRole` | string | 段落角色（如 `hook`/`body`/`climax`/`outro`） |
| `matchedAssetId` | string | 匹配上的素材 ID，MISSING/VETOED 时为空 |
| `matchedHighlightId` | string | 匹配上的素材高光 ID，无则为空 |
| `matchScore` | number | 匹配得分，`[0.0, 1.0]` |
| `matchStatus` | enum | 匹配状态。枚举：`MATCHED` / `PARTIAL` / `MISSING` / `VETOED` |
| `vetoReason` | string | 否决原因。当状态为 `MISSING` 或 `VETOED` 时必须填写 |
| `matchReason` | string | 匹配成功的自然语言解释 |
| `adaptationPlan` | object | 适配策略计划，见下方说明 |

### matchDecisions[].imageGen 字段群（素材缺口补全）

当 `matchStatus` 为 `MISSING` 时，由 LLM 判断并输出：

| 字段 | 类型 | 说明 |
|------|------|------|
| `imageGenEligible` | boolean | 是否满足 AI 补图条件（属于 UI 装饰/贴纸/Logo/插画/背景等类型） |
| `imageGenCategory` | enum | 补图分类。枚举：`UI_ELEMENT` / `STICKER` / `LOGO` / `ILLUSTRATION` / `BACKGROUND` / `NONE` |
| `imageGenPrompt` | string | 用于调用文生图服务的**英文**提示词，须附带质量控制与透明背景指令 |
| `imageGenDescription` | string | 生成图的**中文**自然语言摘要，注入到后续编排卡片中 |

### matchDecisions[].adaptationPlan.strategyChain[] 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `strategyType` | enum | 策略类型。枚举：`TRIM_AND_CUT` / `SMART_CROP` / `LIGHTING_ADJUST` / `LOCAL_BLUR` / `LOOP_SEQUENCE` / `KEN_BURNS_MOTION` |
| `params` | object | 具体策略的执行参数，如 `startTime`, `endTime`, `boundingBox`, `brightnessPercent` 等 |

---

<a id="appendix-d"></a>
## 附录 D：CompositionScript JSON 字段说明

[← 返回正文 3.2.4 节](#324-remotion-渲染编排-jsoncomposition-scriptv1)

### 根节点字段白名单

| 字段 | 类型 | 说明 |
|------|------|------|
| `$schema` | string | 固定为 `composition-script/v1` |
| `projectId` | string | 创作项目 ID |
| `canvas` | object | 画布配置。含 `width`(int)、`height`(int)、`fps`(int)，由系统注入，LLM 必须服从 |
| `globalStyle` | object | 全局样式。含 `fontFamily`(string)、`fontTier`(enum)、`backgroundColor`(string)。`fontTier` 枚举见 §3.2.4 字体分层表 |
| `bgm` | object | 全局背景音乐（见下方 bgm 字段） |
| `scenes` | array | 场景序列（非空） |
| `transitions` | array | 转场数组，无转场时输出 `[]` |

### bgm 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| `src` | string | BGM 音频 URL |
| `mixLevel` | enum | 混音等级。`QUIET`=弱背景氛围、`BALANCED`=明确存在感但不喧宾夺主、`DRIVE`=节奏推动与情绪驱动 |
| `loop` | boolean | 是否循环 |
| `fadeInFrames` | integer | 淡入帧数 |
| `fadeOutFrames` | integer | 淡出帧数 |

### scene 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| `sceneId` | string | 场景唯一标识 |
| `sceneIndex` | integer | 场景序号 |
| `role` | string | 段落职责（可选，如 `hook`/`body`/`climax`/`outro`） |
| `durationInFrames` | integer | 场景持续帧数（正整数，30fps 下 120 帧 = 4 秒） |
| `layers` | array | 图层数组 |

### layer 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| `layerId` | string | 图层唯一标识 |
| `preset` | string | 预设组件 ID（见正文预设清单表） |
| `enterAtFrame` | integer | 入场帧（可选，默认 0） |
| `durationInFrames` | integer | 持续帧数（可选） |
| `params` | object | 预设参数对象（必须为对象，禁止 null） |

### transition 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| `fromSceneIndex` | integer | 起始场景序号 |
| `toSceneIndex` | integer | 目标场景序号 |
| `preset` | string | 转场预设。枚举：`transition.fade` / `transition.slide` / `transition.wipe` |
| `params` | object | 参数。含 `durationInFrames`(int)、`timing`(`linear`/`spring`)；`slide` 额外含 `direction` |
| `overlay` | object | 转场叠加效果（可选）。含 `preset`(string)、`params`(object) |

### media.audio 音效层参数（params）

| 字段 | 类型 | 说明 |
|------|------|------|
| `src` | string | 音频 URL。当 `cueType` 已指定时可省略（后端自动匹配内置音效） |
| `volume` | number | 音量 |
| `loop` | boolean | 是否循环 |
| `audioRole` | enum | 职责。`sfx_loop`=持续音效、`sfx_cue`=短促触发音、`voice_support`=辅助语音 |
| `cueType` | enum | 语义类型。`typewriter_loop` / `type_end_click` / `emphasis_hit` / `transition_whoosh` / `brand_logo_hit` / `ui_click` |
| `syncWithLayerId` | string | 同步目标图层的 layerId |
| `syncMode` | enum | 同步模式。`match_layer`=同步起止、`match_typing`=同步到打字结束、`trigger_on_start`=层开始时触发、`trigger_on_end`=层结束时触发、`trigger_on_typing_end`=打字完成时触发 |
| `offsetFrames` | integer | 同步微调偏移（正=延后，负=提前） |

---

<a id="appendix-e"></a>
## 附录 E：Remotion 预设组件参数白名单 (layer.params)

[← 返回正文 3.2.4 节](#324-remotion-渲染编排-jsoncomposition-scriptv1)

本协议定义了 `composition-script/v1` 中每个 `layer` 节点内 `params` 对象允许传入的字段（小协议）。LLM 必须严格服从对应的字段结构，禁止虚构参数。

### 1. 背景预设 (bg.*)

#### `bg.mesh_gradient`
渐变网格背景。
```json
{
  "preset": "bg.mesh_gradient",
  "params": {
    "colors": ["#0F2027", "#203A43", "#7C3AED"],
    "intensity": 0.92,
    "blendMode": "normal",
    "layoutMode": "auto"
  }
}
```

#### `bg.tech_grid`
科技网格背景。
```json
{
  "preset": "bg.tech_grid",
  "params": {
    "backgroundColor": "#000000",
    "lineColor": "#333333",
    "accentColor": "#00FF00",
    "gridSize": 40,
    "lineOpacity": 0.5,
    "lineWidth": 1,
    "driftSpeed": 1.5,
    "layoutMode": "auto"
  }
}
```

#### `bg.noise_grain`
噪点纹理，`backgroundColor` 允许使用 `transparent`。
```json
{
  "preset": "bg.noise_grain",
  "params": {
    "backgroundColor": "transparent",
    "grainOpacity": 0.15,
    "scale": 1.0,
    "layoutMode": "auto"
  }
}
```

### 2. 媒体预设 (media.*)

#### `media.video`
基础视频层。
```json
{
  "preset": "media.video",
  "params": {
    "src": "https://...",
    "trimBefore": 0,
    "trimAfter": 10,
    "volume": 1.0,
    "playbackRate": 1.0,
    "loop": false,
    "muted": false,
    "style": { "objectFit": "cover" }
  }
}
```

#### `media.image`
基础图片层。LOGO/插图优先使用此预设，搭配 `objectFit: contain`。
```json
{
  "preset": "media.image",
  "params": {
    "src": "https://...",
    "style": { "objectFit": "contain", "position": { "x": "center", "y": "center" } }
  }
}
```

#### `media.audio`
局部音效层。详细枚举见附录 D。
```json
{
  "preset": "media.audio",
  "params": {
    "src": "https://...",
    "volume": 1.0,
    "loop": false,
    "trimBefore": 0,
    "trimAfter": 2.5,
    "fadeInFrames": 10,
    "fadeOutFrames": 10,
    "totalDurationFrames": 60,
    "audioRole": "sfx_cue",
    "cueType": "ui_click",
    "syncWithLayerId": "layer_0_title",
    "syncMode": "match_typing",
    "offsetFrames": 0
  }
}
```

### 3. 运动预设 (motion.*)

#### `motion.ken_burns`
图片平移缩放动效。
```json
{
  "preset": "motion.ken_burns",
  "params": {
    "src": "https://...",
    "startScale": 1.0,
    "endScale": 1.2,
    "startPosition": { "x": "center", "y": "center" },
    "endPosition": { "x": "center", "y": "top" },
    "easing": [0.25, 0.1, 0.25, 1.0]
  }
}
```

#### `motion.float_2d5`
2.5D 悬浮卡片效果，适合 UI 元素/贴纸。
```json
{
  "preset": "motion.float_2d5",
  "params": {
    "src": "https://...",
    "floatAmplitude": 20,
    "floatSpeed": 1.5,
    "swayAmount": 5,
    "scaleBreath": 1.05,
    "perspective": 1000,
    "shadowEnabled": true,
    "shadowColor": "rgba(0,0,0,0.5)",
    "objectFit": "contain",
    "scale": 1.0
  }
}
```

#### `motion.parallax_drift`
连续循环视差漂移，适合氛围背景。
```json
{
  "preset": "motion.parallax_drift",
  "params": {
    "src": "https://...",
    "driftRangeX": 50,
    "driftRangeY": 30,
    "driftSpeedX": 0.5,
    "driftSpeedY": 0.8,
    "scaleRange": 1.1,
    "rotationRange": 5,
    "objectFit": "cover"
  }
}
```

#### `motion.perspective_tilt`
3D 透视倾斜（如 Apple TV 卡片），适合高端产品展示。
```json
{
  "preset": "motion.perspective_tilt",
  "params": {
    "src": "https://...",
    "rotateX": -5,
    "rotateY": 3,
    "perspective": 800,
    "scale": 1.0,
    "dynamicEnabled": true,
    "dynamicRange": 2,
    "shadowEnabled": true,
    "shadowColor": "rgba(0,0,0,0.4)",
    "objectFit": "cover"
  }
}
```

### 4. 文本与字幕预设 (text.* / caption.*)

#### `text.fade_title`
基础淡入标题。
```json
{
  "preset": "text.fade_title",
  "params": {
    "text": "示例标题",
    "fontSize": 72,
    "color": "#FFFFFF",
    "fontWeight": 700,
    "position": { "x": "center", "y": "center" },
    "positionPreset": "hero_center",
    "layoutMode": "auto",
    "easing": [0.25, 1, 0.5, 1],
    "textShadow": "0px 4px 10px rgba(0,0,0,0.5)",
    "maxWidth": "80%"
  }
}
```

#### `text.kinetic_pop`
弹性弹跳动效标题。默认字体 Ma Shan Zheng 书法体（`fontTier: "accent"`）。
```json
{
  "preset": "text.kinetic_pop",
  "params": {
    "text": "弹跳标题",
    "fontSize": 96,
    "color": "#FFDD00",
    "fontWeight": 900,
    "position": { "x": "center", "y": "center" },
    "positionPreset": "hero_center",
    "layoutMode": "auto",
    "fontTier": "accent",
    "scaleFrom": 0.5,
    "scaleTo": 1.0,
    "rotationFrom": -10,
    "rotationTo": 0,
    "enterFrames": 15,
    "settleFrames": 10,
    "textShadow": "0px 8px 16px rgba(0,0,0,0.4)",
    "letterSpacing": "2px",
    "textAlign": "center",
    "maxWidth": "90%"
  }
}
```

#### `text.hero_billboard`
强排版复合块。默认字体 ZCOOL XiaoWei（`fontTier: "title"`）。
```json
{
  "preset": "text.hero_billboard",
  "params": {
    "text": "主标题",
    "texts": ["副标题1", "副标题2"],
    "layoutPattern": "center_focus",
    "animationMode": "staggered",
    "easingPreset": "spring_bounce",
    "layoutMode": "auto",
    "fontSize": 120,
    "color": "#FFFFFF",
    "accentColor": "#FF0055",
    "fontWeight": 900,
    "fontTier": "title",
    "letterSpacing": "4px",
    "textShadow": "0px 10px 20px rgba(0,0,0,0.6)",
    "strokeEnabled": true,
    "strokeColor": "#000000",
    "strokeWidth": 4,
    "glowColor": "rgba(255,0,85,0.5)",
    "glowBlur": 20,
    "glowOpacity": 0.8,
    "maxWidth": "90%",
    "charIntervalFrames": 2,
    "staggerFrames": 5,
    "lineGap": 20
  }
}
```

#### `text.typewriter`
打字机效果标题。
```json
{
  "preset": "text.typewriter",
  "params": {
    "text": "正在输入中...",
    "fontSize": 48,
    "color": "#00FFCC",
    "fontWeight": 500,
    "position": { "x": "left", "y": "center" },
    "positionPreset": "left_focus",
    "layoutMode": "auto",
    "charIntervalFrames": 3,
    "cursor": "|",
    "cursorColor": "#FFFFFF",
    "cursorScale": 1.2,
    "textShadow": "0px 2px 4px rgba(0,0,0,0.5)",
    "maxWidth": "80%",
    "letterSpacing": "1px",
    "textAlign": "left"
  }
}
```

#### `text.mask_reveal`
遮罩揭示效果标题。
```json
{
  "preset": "text.mask_reveal",
  "params": {
    "text": "揭示内容",
    "fontSize": 80,
    "color": "#FFFFFF",
    "fontWeight": 800,
    "position": { "x": "center", "y": "center" },
    "positionPreset": "hero_center",
    "layoutMode": "auto",
    "revealDirection": "left_to_right",
    "revealFrames": 20,
    "textShadow": "none",
    "letterSpacing": "0px",
    "textAlign": "center",
    "maxWidth": "90%",
    "maskPadding": 10
  }
}
```

#### `text.word_highlight`
KTV 逐字/高光词提示。
```json
{
  "preset": "text.word_highlight",
  "params": {
    "text": "高光词突出显示",
    "tokens": ["高光词", "突出", "显示"],
    "highlightWords": ["高光词"],
    "fontSize": 64,
    "color": "#AAAAAA",
    "highlightColor": "#FFFFFF",
    "highlightBackground": "rgba(255,255,255,0.2)",
    "fontWeight": 700,
    "position": { "x": "center", "y": "bottom" },
    "positionPreset": "hero_lower",
    "layoutMode": "auto",
    "wordDurationInFrames": 15,
    "textShadow": "0px 2px 4px rgba(0,0,0,0.8)",
    "gap": 12,
    "textAlign": "center",
    "highlightScale": 1.1
  }
}
```

#### `text.counter_number`
滚动数字（数据展示/价格强调）。
```json
{
  "preset": "text.counter_number",
  "params": {
    "value": 99.9,
    "prefix": "¥",
    "suffix": "起",
    "decimals": 1,
    "fontSize": 140,
    "color": "#FF4444",
    "fontWeight": 900,
    "scrollFrames": 30,
    "digitGap": 5,
    "position": { "x": "center", "y": "center" },
    "layoutMode": "auto",
    "textShadow": "0px 4px 8px rgba(0,0,0,0.3)",
    "letterSpacing": "0px"
  }
}
```

#### `text.label_chip`
品类标签/关键词胶囊。
```json
{
  "preset": "text.label_chip",
  "params": {
    "text": "限时特惠",
    "variant": "filled",
    "color": "#FFFFFF",
    "bgColor": "#FF0000",
    "borderColor": "transparent",
    "fontSize": 32,
    "fontWeight": 600,
    "paddingX": 16,
    "paddingY": 8,
    "borderRadius": 100,
    "position": { "x": "right", "y": "top" },
    "icon": "🔥",
    "enterFrames": 10,
    "textShadow": "none"
  }
}
```

#### `caption.subtitle`
常规字幕组件。
```json
{
  "preset": "caption.subtitle",
  "params": {
    "text": "这是一句底部字幕",
    "fontSize": 42,
    "color": "#FFFFFF",
    "bgColor": "rgba(0,0,0,0.6)",
    "position": "bottom_center"
  }
}
```

### 5. 叠加与衬底预设 (overlay.* / backing.*)

#### `overlay.light_leak`
随机漏光氛围叠加层。
```json
{
  "preset": "overlay.light_leak",
  "params": {
    "seed": 12345,
    "hueShift": 30,
    "durationInFrames": 90
  }
}
```

#### `overlay.flash`
闪白转场叠加。
```json
{
  "preset": "overlay.flash",
  "params": {
    "color": "#FFFFFF",
    "maxOpacity": 1.0,
    "enterFrames": 5,
    "holdFrames": 2,
    "exitFrames": 10
  }
}
```

#### `overlay.badge_pop`
促销角标弹出。
```json
{
  "preset": "overlay.badge_pop",
  "params": {
    "text": "HOT",
    "bgColor": "#FF2200",
    "color": "#FFFFFF",
    "fontSize": 48,
    "fontWeight": 900,
    "position": { "x": "80%", "y": "20%" },
    "scaleFrom": 0,
    "scaleTo": 1.0,
    "rotation": 15,
    "paddingX": 24,
    "paddingY": 12,
    "borderRadius": 8,
    "shadowColor": "rgba(0,0,0,0.5)",
    "borderColor": "#FFFFFF"
  }
}
```

#### `overlay.glow_frame`
发光呼吸边框。
```json
{
  "preset": "overlay.glow_frame",
  "params": {
    "color": "#00FFCC",
    "thickness": 8,
    "glowBlur": 20,
    "opacity": 0.8,
    "borderRadius": 16,
    "pulseStrength": 0.3,
    "inset": 10
  }
}
```

#### `backing.solid_plate`
文字纯色衬底垫层。
```json
{
  "preset": "backing.solid_plate",
  "params": {
    "width": "80%",
    "height": 200,
    "color": "#0B1120",
    "opacity": 0.65,
    "borderRadius": 24,
    "padding": 32,
    "position": { "x": "center", "y": "center" },
    "borderColor": "transparent",
    "borderWidth": 0,
    "shadowEnabled": true,
    "shadowColor": "rgba(0,0,0,0.8)",
    "enterFrames": 15
  }
}
```

#### `backing.capsule`
胶囊状文字底板。
```json
{
  "preset": "backing.capsule",
  "params": {
    "width": 400,
    "height": 80,
    "color": "#FFFFFF",
    "opacity": 0.9,
    "borderRadius": 999,
    "paddingX": 40,
    "paddingY": 20,
    "position": { "x": "center", "y": "center" },
    "borderColor": "#DDDDDD",
    "borderWidth": 2,
    "shadowEnabled": true,
    "shadowColor": "rgba(0,0,0,0.2)",
    "enterFrames": 10,
    "scaleFrom": 0.5
  }
}
```

#### `backing.glass_plate`
毛玻璃质感背景卡片。
```json
{
  "preset": "backing.glass_plate",
  "params": {
    "width": "90%",
    "height": "60%",
    "blurAmount": 20,
    "tintColor": "rgba(255,255,255,0.12)",
    "borderRadius": 32,
    "borderColor": "rgba(255,255,255,0.2)",
    "borderWidth": 1,
    "padding": 40,
    "position": { "x": "center", "y": "center" },
    "shadowEnabled": true,
    "shadowColor": "rgba(0,0,0,0.4)",
    "enterFrames": 20
  }
}
```
