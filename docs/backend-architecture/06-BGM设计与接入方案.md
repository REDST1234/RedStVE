# 批次 6：BGM 设计与接入方案

## 6.1 目标与范围

本文档用于统一本项目中背景音乐（BGM）相关能力的设计口径，覆盖以下问题：

1. BGM 推荐结果如何进入创作主链，而不是停留在独立调试能力；
2. 项目级“已选 BGM”如何落库、如何回查、如何参与编排；
3. FFmpeg 适配链如何消费 BGM，特别是 `AUDIO_DUCKING`、纯 BGM 覆盖、无原音视频兜底等场景；
4. Remotion 最终渲染如何使用同一份 BGM 事实源；
5. 如何避免“推荐协议一套、编排协议一套、渲染协议又一套”的多口径漂移。

本文档不重新设计 BGM 向量召回算法本身；该部分已在 [04-创作链路与素材适配模块](./04-%E5%88%9B%E4%BD%9C%E9%93%BE%E8%B7%AF%E4%B8%8E%E7%B4%A0%E6%9D%90%E9%80%82%E9%85%8D%E6%A8%A1%E5%9D%97.md) 的 `4.14 智能 BGM 推荐子系统设计` 中说明。本文重点是：**如何把推荐结果真正接进创作与渲染闭环**。

---

## 6.2 当前现状与缺口判断

### 6.2.1 已具备能力

当前系统已具备以下 BGM 相关基础：

1. **独立的 BGM 推荐子系统**
   - 支持基于素材画像 + 模板快照进行 BGM 召回与精排；
   - 已有独立 API：`POST /api/v1/creation/projects/{projectId}/recommend-bgm`；
   - 已有独立向量集合：`creation_bgm`。

2. **Remotion 编排协议层支持 BGM**
   - Java 侧 `CompositionScript` 已定义 `bgm` 字段；
   - TypeScript 侧 `CompositionScriptSchema` 已定义顶层 `bgm`；
   - `DynamicVideoRenderer` 会将 `script.bgm` 渲染为全局音轨。

3. **FFmpeg 侧具备 BGM 相关执行器基础**
   - `AUDIO_DUCKING` 已具备 `sidechaincompress + amix` 的协议能力；
   - 无原音轨素材时已有 `anullsrc` 兜底思路。

### 6.2.2 当前缺口

虽然上述能力都存在，但当前仍未形成完整闭环：

1. **推荐结果未进入项目主状态**
   - 当前 `recommend-bgm` 更像单独调试能力；
   - 项目表 `creation_project` 尚未持久化“当前已选 BGM”；
   - 因此 BGM 没有项目级事实源。

2. **编排主链没有消费“已选 BGM”**
   - `VideoOrchestrationService` 目前主要基于 `projectDescription + assetsJson` 编排；
   - 没有明确将“已选 BGM”注入 `CompositionScript.bgm`。

3. **FFmpeg 适配链尚未显式接入 BGM 文件源**
   - `AUDIO_DUCKING` 假设存在第二路 `[1:a]`；
   - 但当前编排器并未统一维护“所选 BGM 文件路径 -> 第二音轨输入”的接线逻辑。

4. **协议口径存在漂移风险**
   - Remotion 编排 Prompt 中存在 `media.audio` 语义；
   - 但最终渲染器消费的是顶层 `bgm`；
   - 若不统一，会出现“模型输出了音频层，但渲染器期待的是顶层 BGM”的错位。

结论：**当前 BGM 处于“推荐能力已实现、渲染协议已预留、主创作链尚未真正闭环”的阶段。**

---

## 6.3 设计原则

### 6.3.1 单一事实源原则

项目级 BGM 必须有且仅有一个主事实源，推荐采用：

- **项目当前选中 BGM = 项目 BGM 绑定记录**

所有下游能力都应从这里读取，而不是各自再做一轮猜测：

- FFmpeg 适配链从这里读取；
- Remotion 编排链从这里读取；
- 前端工作台从这里读取；
- 调试页面从这里读取。

### 6.3.2 顶层 `bgm` 为全局配乐唯一入口

最终统一以下协议语义：

- `CompositionScript.bgm`：**全局背景音乐主通道**
- `media.audio`：**局部音效 / 特殊声音层 / future SFX**

禁止将全局 BGM 再混入普通场景层，以避免：

- 统一调音困难；
- loop / fade 失控；
- ducking 规则分散；
- 前后端协议理解不一致。

### 6.3.3 `mixLevel` 与 `volume` 分层原则

为避免让 LLM 直接输出脆弱的小数音量，本项目统一采用“两层语义”：

1. `mixLevel`：**创作语义层**
   - 由 LLM 输出；
   - 只能从 `QUIET / BALANCED / DRIVE` 中选择；
   - 表示“背景音乐在作品中的存在感档位”；

2. `volume`：**执行物理层**
   - 由 Java 后端计算；
   - 最终传给 Remotion / FFmpeg 执行；
   - 不允许由 LLM 直接输出。

当前默认映射如下：

| `mixLevel` | 语义 | 默认 `volume` |
| --- | --- | --- |
| `QUIET` | 弱背景氛围，不压口播与字幕 | `0.18` |
| `BALANCED` | 明确存在感，但不喧宾夺主 | `0.28` |
| `DRIVE` | 明显节奏推动与情绪驱动 | `0.38` |

> 说明：后续若 BGM 资产库补充 `loudnessMetrics`（如 `integratedLufs`、`truePeakDbtp`），Java 可在此映射之上继续做响度补偿，但 LLM 仍然只输出 `mixLevel`。

### 6.3.4 推荐与选择分离

推荐不是绑定。系统必须区分：

1. **推荐结果**：给出 TopN 候选；
2. **用户选择结果**：某个候选被绑定为项目当前 BGM；
3. **编排使用结果**：创作主链使用该绑定结果进入 FFmpeg / Remotion。

### 6.3.5 “先项目绑定，再策略消费”

任何 `AUDIO_DUCKING`、BGM loop、fade、最终渲染都不应该直接依赖临时请求参数，而应该依赖：

- 项目已绑定的 BGM 记录；
- 或者明确版本化的 BGM 绑定快照。

### 6.3.6 可解释、可替换、可回放

BGM 接入必须满足：

- 能回答“当前项目用的是哪首 BGM”
- 能回答“为什么推荐这首”
- 能替换为人工上传或人工指定
- 能在后续重新编排 / 重新渲染时复现同一结果

---

## 6.4 目标闭环架构

### 6.4.1 端到端链路

```
用户绑定模板 -> 上传并分析素材 -> BGM 推荐 -> 选择 BGM -> 持久化绑定
                                                    │
                                                    ▼
                                    槽位匹配 / 缺口识别 / 策略生成
                                                    │
                                                    ├── FFmpeg 适配链消费 BGM
                                                    │     - AUDIO_DUCKING
                                                    │     - 无原音视频叠加 BGM
                                                    │
                                                    ▼
                                    CompositionScript 生成时注入 top-level bgm
                                                    │
                                                    ▼
                                          Remotion 渲染最终成片
```

### 6.4.2 生命周期阶段

1. **Recommend**
   - 根据素材画像 + 模板快照给出 TopN 候选；

2. **Select**
   - 用户或系统自动从候选中选中 1 首 BGM；

3. **Persist**
   - 将该选择写入项目级 BGM 绑定记录；

4. **Adapt**
   - FFmpeg 适配阶段按策略链消费该 BGM；

5. **Render**
   - Remotion 使用同一事实源注入 `CompositionScript.bgm`；

6. **Replay**
   - 后续重新生成 / 重新导出时可基于同一 BGM 事实源复现。

---

## 6.5 数据模型设计

### 6.5.1 设计选择

不建议把所有 BGM 字段直接平铺进 `creation_project`。虽然项目当前只允许绑定 1 首主 BGM，但后续仍然会遇到：

- 手动改绑历史；
- 自动推荐版本差异；
- 不同编排版本复用同一项目；
- 需要记录推荐来源与人工覆盖来源。

因此推荐新增独立子表：

### 6.5.2 推荐新增表：`creation_project_bgm_binding`

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 自增主键 |
| `biz_id` | BIGINT | 雪花业务主键 |
| `project_id` | VARCHAR(64) | 创作项目 ID |
| `version_id` | VARCHAR(64) | 可选，绑定时对应的创作版本；P0 可先允许空 |
| `audio_id` | VARCHAR(128) | BGM 资产唯一标识（向量库 / 音频库中的逻辑 ID） |
| `audio_name` | VARCHAR(255) | 展示名称 |
| `src_path` | VARCHAR(1024) | 最终 BGM 物理或可访问路径 |
| `source_type` | VARCHAR(32) | `RECOMMENDED` / `MANUAL_UPLOAD` / `MANUAL_PICK` |
| `recommend_score` | DOUBLE | 若来自推荐，记录最终得分 |
| `semantic_score` | DOUBLE | 可选，语义子分 |
| `energy_curve_score` | DOUBLE | 可选，能量匹配子分 |
| `duration_bpm_score` | DOUBLE | 可选，物理对齐子分 |
| `mix_level` | VARCHAR(32) | LLM 输出的语义混音档位：`QUIET / BALANCED / DRIVE` |
| `volume` | DOUBLE | 后端根据 `mix_level` 计算出的最终执行音量，建议 `[0,1]` |
| `loop_enabled` | TINYINT(1) | 是否允许循环 |
| `fade_in_frames` | INT | Remotion / 混音默认淡入帧数 |
| `fade_out_frames` | INT | Remotion / 混音默认淡出帧数 |
| `ducking_enabled` | TINYINT(1) | 是否允许对该 BGM 做闪避 |
| `ducking_ratio` | DOUBLE | 闪避比例，如 `0.2` |
| `metadata_json` | JSON | 其余扩展信息，如风格、BPM、时长、文件来源 |
| `status` | VARCHAR(32) | `SELECTED` / `REPLACED` / `DISABLED` |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |
| `deleted_at` | DATETIME | 软删除 |

### 6.5.3 为什么不只改 `creation_project`

若仅在 `creation_project` 上追加：

- `selected_bgm_audio_id`
- `selected_bgm_src`
- `selected_bgm_volume`

虽然 P0 可以跑通，但缺点明显：

- 无法记录替换历史；
- 无法表达推荐来源与人工来源；
- 无法优雅支持版本化编排；
- 后续审计不清晰。

因此本文推荐：**项目主表只保留“是否存在当前 BGM”这种轻状态；具体绑定信息下沉到独立表。**

### 6.5.4 当前 BGM 资产库 `audio_data.json` 的同步改造建议

当前音频库 JSON 主要包含：

- `audioId`
- `audioName`
- `filePath`
- `globalMetrics`
- `auditoryTimeline`

这能支撑推荐与时序感知，但还不足以支撑“项目级绑定 + 混音策略 + 后续响度补偿”。建议在不破坏旧字段的前提下，向每个 `audio_data.json` 同步补齐以下结构：

```json
{
  "audioId": "bgm_future_synth_001",
  "audioName": "未来幻象",
  "filePath": "708973__jghoffman418__future-86bpm-e-min.wav",
  "globalMetrics": {
    "bpm": 86,
    "overallStyle": "ambient_synthwave",
    "durationSeconds": 37.0
  },
  "mixDefaults": {
    "defaultMixLevel": "BALANCED",
    "loopRecommended": true,
    "fadeInFrames": 15,
    "fadeOutFrames": 30,
    "duckingRecommended": true,
    "duckingRatio": 0.2
  },
  "loudnessMetrics": {
    "integratedLufs": -18.7,
    "truePeakDbtp": -1.1,
    "loudnessRangeLra": 6.3
  },
  "auditoryTimeline": []
}
```

字段解释：

- `mixDefaults.defaultMixLevel`
  - 音频资产自己的默认语义档位；
  - 当前推荐值可作为 `QUIET / BALANCED / DRIVE` 的默认候选，而不是最终执行值。

- `mixDefaults.loopRecommended`
  - 是否适合循环使用；
  - 例如明显有突兀起止点的音乐，不一定适合 loop。

- `mixDefaults.fadeInFrames / fadeOutFrames`
  - 为项目选择后的默认淡入淡出参数提供素材级建议；
  - 后端仍可覆盖。

- `mixDefaults.duckingRecommended / duckingRatio`
  - 为有口播项目提供默认闪避建议；
  - 后端和项目级绑定仍然是最终事实源。

- `loudnessMetrics`
  - 为下一阶段的“`mixLevel -> volume` + 响度补偿”打基础；
  - 当前 P0 可以先不消费，但强烈建议开始补齐。

同步原则：

1. **旧字段不删，只增不改**
   - 保持现有推荐链不回归。

2. **`mixDefaults` 是素材级默认建议，不是项目级最终绑定**
   - 项目一旦执行 `select-bgm`，应以项目绑定表为准。

3. **`loudnessMetrics` 先做离线补齐，再逐步接入后端映射**
   - 不建议让 LLM 直接根据主观听感输出小数音量。

---

## 6.6 API 设计

### 6.6.1 现有保留接口

继续保留：

- `POST /api/v1/creation/projects/{projectId}/recommend-bgm`

用途：

- 返回 TopN 候选，不做持久化绑定。

### 6.6.2 新增：选择 BGM（已实现）

`POST /api/v1/creation/projects/{projectId}/bgm/select`

请求体示例：

```json
{
  "audioId": "bgm_001",
  "audioName": "Lo-fi Product Pulse",
  "srcPath": "/api/storage/audio-database/folder/demo.mp3",
  "sourceType": "RECOMMENDED",
  "recommendScore": 0.88,
  "mixLevel": "BALANCED",
  "loopEnabled": true,
  "fadeInFrames": 15,
  "fadeOutFrames": 30,
  "duckingEnabled": true,
  "duckingRatio": 0.2,
  "metadata": {
    "bpm": 118,
    "overallStyle": "clean_product_lofi",
    "durationSeconds": 42.0
  }
}
```

语义：

- 将该 BGM 写为项目当前选中 BGM；
- 若项目已有 `SELECTED` 记录，则旧记录转为 `REPLACED`；
- 当前项目后续编排、适配、渲染一律读取最新 `SELECTED`。

### 6.6.3 新增：查询当前 BGM（已实现）

`GET /api/v1/creation/projects/{projectId}/bgm`

返回当前项目已选 BGM；若无绑定则返回空对象或业务空态。

### 6.6.4 新增：取消/替换 BGM（已实现）

`DELETE /api/v1/creation/projects/{projectId}/bgm`

语义：

- 逻辑失效当前 `SELECTED` 记录；
- 后续编排恢复“无全局 BGM”路径。

---

## 6.7 与创作主链的接入方式

### 6.7.1 推荐接入点

推荐将 BGM 步骤放在：

1. 模板已绑定；
2. 素材已完成画像；
3. 缺口识别前后均可访问；
4. 正式生成前必须完成“是否选中 BGM”的确认。

推荐的工作流顺序：

```
创建项目 -> 绑定模板 -> 上传素材 -> 素材分析完成
         -> 推荐 BGM -> 选择 BGM
         -> 素材缺口识别 -> 素材适配 -> 生成 CompositionScript -> Remotion 渲染
```

### 6.7.2 主链服务职责

创作主链应新增一个“项目当前 BGM 加载”能力，供以下服务复用：

1. `CreationProjectServiceImpl`
   - 生成最终成片前，读取已选 BGM；
   - 已实现：构建 `selectedBgmJson` 输入给 `VideoOrchestrationService`；
   - 已实现：对 `CompositionScript.bgm` 做最终覆盖，确保 `src` 以项目当前已选 BGM 为准；

2. `AdaptationOrchestratorServiceImpl`
   - 若策略链中存在 `AUDIO_DUCKING` 或“纯 BGM 覆盖”策略，则读取已选 BGM；
   - 当前状态：**尚未完成与项目级 BGM 绑定表的正式接线**，仍属于下一阶段；

3. `VideoOrchestrationService`
   - 生成 `CompositionScript` 时注入 `bgm` 顶层对象；
   - 已实现：把 `selectedBgmJson` 作为独立输入喂给 LLM；
   - 已实现：对 `mixLevel` 做后端归一化，并映射为最终 `volume`。

### 6.7.3 严禁“推荐即自动渲染”

推荐结果不应自动进入渲染。必须有“选择/确认”动作。原因：

- 推荐只是候选，不等于用户最终审美选择；
- 避免每次推荐参数调试都污染项目状态；
- 保持主链稳定与可解释。

---

## 6.8 FFmpeg 适配链接入方案

### 6.8.1 统一原则

FFmpeg 阶段对 BGM 的使用只分两类：

1. **局部混音处理**
   - 例如 `AUDIO_DUCKING`
   - 目标是在片段级输出里先做基础混音

2. **全局成片配乐**
   - 由 Remotion 顶层 `bgm` 负责

P0 推荐策略：**不要在每个片段都硬叠完整 BGM**。  
片段适配阶段只在“确有必要”时做局部混音，否则保留源视频音轨，最终全局配乐交给 Remotion。

### 6.8.2 `AUDIO_DUCKING` 的完整接法

当前 `AUDIO_DUCKING` 已假设：

- `[0:a]` = 原视频口播音轨
- `[1:a]` = BGM

要让它真正闭环，编排器必须在命令组装阶段：

1. 读取当前项目已选 BGM 的 `src_path`
2. 将该 BGM 作为**额外输入**注入 ffmpeg 命令
3. 保证 `filter_complex` 中 `[1:a]` 真实存在

推荐命令形态：

```bash
ffmpeg -i input_video.mp4 -i selected_bgm.mp3 \
  -filter_complex "[1:a][0:a]sidechaincompress=threshold=0.08:ratio=5.0:attack=200:release=1000[bgm_ducked];[0:a][bgm_ducked]amix=inputs=2:duration=first:dropout_transition=2[outa]" \
  -map 0:v? -map [outa] output.mp4
```

### 6.8.3 无原音轨视频

若素材本身无音轨：

- `AUDIO_DUCKING` 不应被视为失败；
- 直接退化为“保留 BGM 原声输出”；
- 或改由最终 Remotion 全局 BGM 处理。

### 6.8.4 命令审计要求

所有使用 BGM 的 FFmpeg 命令必须落库到 `creation_ffmpeg_command_log`，至少记录：

- 真实输入路径（包含 BGM 路径）
- 最终完整命令
- 是否启用 ducking
- 出错原因

---

## 6.9 Remotion 接入方案

### 6.9.1 顶层 `bgm` 为最终成片标准通道

最终成片渲染时，`CompositionScript` 顶层必须按如下方式写入：

```json
{
  "projectId": "crp_xxx",
  "canvas": { "width": 1080, "height": 1920, "fps": 30 },
  "bgm": {
    "src": "/api/storage/audio-database/folder/demo.mp3",
    "mixLevel": "BALANCED",
    "volume": 0.28,
    "loop": true,
    "fadeInFrames": 15,
    "fadeOutFrames": 30
  },
  "scenes": [],
  "transitions": []
}
```

### 6.9.2 `media.audio` 的角色重新定义

后续统一语义：

- `bgm`：全局主配乐
- `media.audio`：局部音效、片头片尾音、特殊提示音、环境补声

这样可以避免：

- 场景层反复叠加全局 BGM
- 不同 scene 各自控制音量造成整体风格漂移

### 6.9.3 `VideoOrchestrationService` 的职责调整

`VideoOrchestrationService` 在调用编排 LLM 前，应额外提供：

- 当前项目已选 BGM 摘要

模型只需要做两件事：

1. 决定是否启用该 BGM；
2. 决定全局音量、loop、fadeIn、fadeOut 等参数。

不建议让 LLM 在无事实源前提下“从空气里虚构一首 BGM 路径”。

---

## 6.10 编排 Prompt 与协议统一

### 6.10.1 Prompt 必须新增的输入

在 `REMOTION_ORCHESTRATOR_JSON` 的输入中增加：

- `selectedBgm`

示例：

```json
{
  "audioId": "bgm_001",
  "audioName": "Lo-fi Product Pulse",
  "src": "/api/storage/audio-database/folder/demo.mp3",
  "mixLevel": "BALANCED",
  "loop": true,
  "fadeInFrames": 15,
  "fadeOutFrames": 30,
  "style": "clean_product_lofi",
  "bpm": 118
}
```

### 6.10.2 Prompt 必须新增的刚性约束

1. 若 `selectedBgm` 存在，优先写入顶层 `bgm`
2. `bgm` 中必须输出 `mixLevel`，禁止输出 `volume` 小数
3. 不得将全局 BGM 伪装成普通 `media.audio` layer
4. 若当前项目无已选 BGM，则允许输出 `bgm=null`
5. 若场景需要局部音效，可继续使用 `media.audio`

### 6.10.3 接口契约优先级

最终统一优先级：

1. 项目 BGM 绑定记录
2. 编排 LLM 对其进行 `mixLevel / loop / fade` 级别的语义参数化
3. Java 根据 `mixLevel` 计算最终 `volume`
4. 渲染器消费 `CompositionScript.bgm`

禁止反向由渲染器临时“猜一个 BGM”。

---

## 6.11 状态机与回查设计

### 6.11.1 BGM 绑定状态

建议状态：

- `SELECTED`
- `REPLACED`
- `DISABLED`

### 6.11.2 项目主状态无需新增复杂枚举

P0 不建议为 BGM 单独引入复杂项目状态机。只需要：

- 项目可无 BGM
- 项目可有当前选中 BGM

前端只需根据是否存在 `SELECTED` 的项目 BGM 记录，展示：

- “未选择 BGM”
- “已选择 BGM：xxx”

### 6.11.3 回查入口

前端至少应具备：

1. 当前项目已选 BGM 查看
2. 推荐候选列表查看
3. 当前最终编排脚本中的 `bgm` 查看

---

## 6.12 异常处理与兜底

### 6.12.1 推荐失败

若 BGM 推荐失败：

- 不阻塞项目继续进行；
- 允许用户无 BGM 继续完成视觉编排；
- 允许后续手动补选。

### 6.12.2 BGM 文件失效

若项目已选 BGM 对应物理文件缺失：

- FFmpeg 阶段：记录命令失败原因并跳过 BGM 混音；
- Remotion 阶段：可降级为无顶层 `bgm` 渲染；
- 同时在项目级别标记“BGM 文件不可用”。

### 6.12.3 编排 LLM 输出异常

若 LLM 将全局 BGM 错误写为 `media.audio` layer：

- Java 侧可在 `CompositionScript` 发送前做一次 sanitize：
  - 若顶层 `bgm` 为空，且发现唯一的全局 `media.audio`，可迁移至顶层；
  - 若同时存在多个音频层，则保守保留并记录警告，不自动猜测。

---

## 6.13 推荐实施顺序（P0 -> P1）

### P0：最小闭环

1. 新增 `creation_project_bgm_binding`
2. 新增 `select/get/delete bgm` API
3. 前端工作流中加入“推荐并选择 BGM”
4. `VideoOrchestrationService` 注入顶层 `bgm`
5. Remotion 按 `CompositionScript.bgm` 渲染

当前实现状态：

- `creation_project_bgm_binding`：**已实现**
- `select/get/delete bgm` API：**已实现**
- `generateVideo()` 读取当前项目已选 BGM 并注入编排主链：**已实现**
- `CompositionScript.bgm` 的 `mixLevel -> volume` 归一化：**已实现**
- `AUDIO_DUCKING` 与项目级 BGM 绑定表正式接线：**未完成**

交付结果：

- 项目有当前已选 BGM
- 最终成片可稳定带 BGM 输出

### P1：混音与适配深化

1. `AUDIO_DUCKING` 真正接上第二音轨输入
2. 支持原音 + BGM 智能闪避
3. 命令审计补充 BGM 输入信息

交付结果：

- 片段级声音处理进入主链

### P2：高级能力

1. 手动上传 BGM
2. 多 BGM 版本 A/B 测试
3. 与模板能量曲线更深绑定
4. 局部场景音效 / SFX 与全局 BGM 并存编排

---

## 6.14 本项目建议结论

最终建议如下：

1. **保留现有 BGM 推荐子系统，不重做召回层**
2. **新增项目级 BGM 绑定记录，作为唯一事实源**
3. **统一顶层 `CompositionScript.bgm` 为全局配乐协议**
4. **FFmpeg 片段适配阶段仅在必要时消费 BGM，主配乐以 Remotion 为准**
5. **先做“推荐 -> 选择 -> 绑定 -> 渲染”最小闭环，再接入 `AUDIO_DUCKING` 完整混音**

这套设计能最大化复用当前已有成果，又能避免：

- 推荐能力游离；
- 编排与渲染协议割裂；
- FFmpeg 和 Remotion 各自拿不同的 BGM 事实源。

---

## 6.15 推荐审查路径

若后续按本文档实施，建议按以下业务顺序审查代码：

1. `CreationProjectController`  
   负责 `recommend-bgm / select-bgm / get-bgm / delete-bgm`

2. `BgmRecommendServiceImpl`  
   负责 BGM 候选召回与精排

3. `CreationProjectBgmBindingService`（新增）  
   负责项目级当前 BGM 绑定事实源

4. `CreationProjectServiceImpl`  
   负责在生成创作结果前加载当前 BGM 状态

5. `VideoOrchestrationService`  
   负责向 `CompositionScript` 注入顶层 `bgm`

6. `RemotionServiceClient`  
   负责将带有 `bgm` 的脚本提交给渲染服务

7. `AdaptationOrchestratorServiceImpl`  
   负责在 `AUDIO_DUCKING` 场景下将 BGM 作为第二输入接入 FFmpeg

8. `AudioDuckingExecutor`  
   负责具体混音滤镜片段构造

9. `remotion-service/src/DynamicVideoRenderer.tsx`  
   负责最终顶层 `bgm` 的 Remotion 消费与播放
