# 批次 5：Remotion 引擎环境配置

## 5.1 目标与范围

本文档用于约束本项目接入 Remotion 时的运行环境、目录结构、依赖安装方式与前后端集成边界。

本轮目标不是立刻实现 Remotion 渲染链路，而是先把环境配置口径统一，避免后续出现以下问题：

- 前端 Remotion Studio 与现有 Vite React 工程互相污染
- 后端 Java 直接执行 Node 渲染时，找不到浏览器或产物目录
- 本地可跑、服务器不可跑，环境变量与缓存目录不一致
- Remotion 依赖版本不统一，导致 `bundle()` / `renderMedia()` 行为不一致

## 5.2 在本项目中的角色定位

当前仓库可分为两条主链：

- `backend/`：Java 业务中台，负责创作项目、素材解析、模板匹配、适配策略、时间线组织
- `frontend/`：现有业务前端，负责项目工作台、素材上传、可视化结果展示

Remotion 引擎建议作为**独立渲染子域**接入到 `frontend/` 内，但与现有业务页面解耦：

- 业务前端继续负责用户交互
- Remotion 只负责：
  - 组合定义（Compositions）
  - 根据时间线 JSON 渲染预览
  - 输出最终视频

推荐链路如下：

```text
Java 创作编排结果（Composition Timeline JSON）
        │
        ▼
Remotion 渲染输入 DTO
        │
        ▼
Remotion Bundle / Render
        │
        ├── 本地预览：Studio / Player
        └── 服务端导出：renderMedia()
```

## 5.3 目录结构建议

### 5.3.1 推荐落位

建议在 `frontend/` 下新增独立目录：

```text
frontend/
├── src/                     # 现有业务前端
├── remotion/                # 新增：Remotion 组合定义
│   ├── index.ts
│   ├── Root.tsx
│   ├── compositions/
│   │   ├── TimelineComposition.tsx
│   │   └── components/
│   └── types/
├── scripts/                 # 新增：Node 渲染脚本
│   ├── render.mjs
│   └── bundle.mjs
├── package.json
└── tsconfig.json
```

### 5.3.2 为什么不放到 `backend/`

不建议把 Remotion 工程直接放进 `backend/`：

- Remotion 本质是 Node + React 渲染链，不适合与 Java 构建生命周期混在一起
- 浏览器下载缓存、bundle 缓存、渲染临时文件更适合放在前端/存储域
- 后端更适合作为“任务调度器”，而不是直接承载前端渲染工程源码

### 5.3.3 为什么单独建 `remotion/`

不建议直接把 Remotion 组合写进现有 `frontend/src/pages`：

- 业务页面与视频组合的依赖会互相污染
- Remotion 的入口、Composition 注册、渲染 props 与普通路由页面语义不同
- 后续做 bundle 缓存、渲染脚本、SSR 导出时，独立目录更容易维护

## 5.4 依赖安装策略

### 5.4.1 安装位置

Remotion 依赖统一安装在 `frontend/`，不要拆到根目录或 `backend/`。

原因：

- 当前前端已经是独立 `package.json`
- Remotion 组合本身也是 React 代码
- 后续本地 Studio、Node render script、业务前端共用一套前端依赖更简单

### 5.4.2 版本策略

Remotion 相关包必须**同版本锁定**，不要给 `^` 浮动范围。

建议首批使用以下包：

```json
{
  "dependencies": {
    "remotion": "4.0.469",
    "@remotion/player": "4.0.469"
  },
  "devDependencies": {
    "@remotion/cli": "4.0.469",
    "@remotion/bundler": "4.0.469",
    "@remotion/renderer": "4.0.469"
  }
}
```

如果后续要引入静态文件工具或高级转场，再按同版本继续追加其他 `@remotion/*` 包。

### 5.4.3 安装命令建议

在 `frontend/` 执行：

```bash
npm install remotion@4.0.469 @remotion/player@4.0.469
npm install -D @remotion/cli@4.0.469 @remotion/bundler@4.0.469 @remotion/renderer@4.0.469
```

如果团队后续统一切 `pnpm`，也必须保持版本完全一致。

## 5.5 Node、浏览器与操作系统要求

### 5.5.1 Node 版本

项目建议统一 Node LTS，推荐不低于 `Node 18`。

这不是 Remotion 在本项目里的唯一约束，而是基于当前仓库前端现状综合考虑：

- 现有 `frontend/` 已使用 Vite + React + ESM
- 后续 Node render script 将直接跑在前端工程上下文内
- Java 后端若通过 `ProcessBuilder` 调 Node，也应调用同一套 Node LTS

### 5.5.2 浏览器依赖

Remotion 渲染依赖 Chromium。环境分两种：

1. **本地开发**
   - 允许使用 Remotion 自动管理的浏览器
   - 优先保证 Studio 可启动、`renderMedia()` 可跑

2. **服务器 / CI**
   - 必须显式约束浏览器缓存目录与执行账户权限
   - 若服务器禁外网，需提前准备浏览器缓存或固定浏览器可执行文件路径

### 5.5.3 FFmpeg 依赖

Remotion 渲染最终仍依赖 FFmpeg 参与编码。

本项目建议：

- **Remotion 渲染链使用 Remotion 自己的渲染依赖**
- **素材解析 / 适配链继续使用现有本地 FFmpeg 引擎**

不要把“创作前处理 FFmpeg”和“Remotion 渲染编码 FFmpeg”强行混成一个实现层。两者职责不同：

- 当前 Java 侧 FFmpeg：素材裁切、局部模糊、亮度调整、Ken Burns、混音
- Remotion 渲染侧：时间线合成后的最终输出编码

## 5.6 前端 `package.json` 脚本建议

建议在 `frontend/package.json` 新增以下脚本：

```json
{
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "remotion:studio": "remotion studio frontend/remotion/index.ts",
    "remotion:bundle": "node ./scripts/bundle.mjs",
    "remotion:render": "node ./scripts/render.mjs"
  }
}
```

说明：

- `remotion:studio`：本地开发者查看组合与预览
- `remotion:bundle`：生成 bundle，可供服务端复用
- `remotion:render`：直接调用 `@remotion/renderer` 输出 mp4

## 5.7 环境变量设计

为后续 Java 后端调度 Remotion，建议提前统一以下环境变量。

### 5.7.1 前端侧 `.env.remotion`

建议新增：

```bash
REMOTION_ENTRY=frontend/remotion/index.ts
REMOTION_BUNDLE_OUT=storage/remotion/bundles
REMOTION_RENDER_OUT=storage/remotion/renders
REMOTION_BROWSER_CACHE=storage/remotion/browser-cache
REMOTION_TEMP_DIR=storage/remotion/temp
REMOTION_LOG_LEVEL=info
```

### 5.7.2 后端侧调用环境

当 Java 后端通过 `ProcessBuilder` 调用 Node 渲染脚本时，建议透传：

```bash
NODE_ENV=production
REMOTION_ENTRY=frontend/remotion/index.ts
REMOTION_RENDER_OUT=storage/remotion/renders
REMOTION_BROWSER_CACHE=storage/remotion/browser-cache
REMOTION_TEMP_DIR=storage/remotion/temp
```

### 5.7.3 可选环境变量

若线上环境对浏览器路径管控严格，可追加：

```bash
REMOTION_BROWSER_EXECUTABLE=/path/to/chrome-or-chromium
```

本项目建议将其设计为**可选覆盖项**，不要一开始就写死。

## 5.8 存储目录规范

建议在仓库根目录下统一规划：

```text
storage/
├── remotion/
│   ├── bundles/         # bundle 产物
│   ├── renders/         # 最终视频
│   ├── temp/            # 渲染中间文件
│   └── browser-cache/   # 浏览器缓存
```

约束：

- `bundles/`：允许按 hash 或版本号复用，避免每次重新 bundle
- `renders/`：按 `projectId/versionId/exportId` 分层
- `temp/`：允许任务完成后清理
- `browser-cache/`：不要随业务任务删除

## 5.9 本地开发环境配置流程

建议开发者按以下顺序完成环境初始化：

### 第一步：安装前端依赖

```bash
cd frontend
npm install
```

### 第二步：安装 Remotion 依赖

```bash
npm install remotion@4.0.469 @remotion/player@4.0.469
npm install -D @remotion/cli@4.0.469 @remotion/bundler@4.0.469 @remotion/renderer@4.0.469
```

### 第三步：创建目录

```bash
mkdir remotion
mkdir scripts
mkdir ..\\storage\\remotion
mkdir ..\\storage\\remotion\\bundles
mkdir ..\\storage\\remotion\\renders
mkdir ..\\storage\\remotion\\temp
mkdir ..\\storage\\remotion\\browser-cache
```

### 第四步：验证 Studio

```bash
npm run remotion:studio
```

验证目标：

- Studio 能正常打开
- 能识别 `frontend/remotion/index.ts`
- 不出现浏览器缺失或权限错误

### 第五步：验证 Node Renderer

```bash
npm run remotion:render
```

验证目标：

- Node 端可调用 `bundle()` / `renderMedia()`
- 最终产物能写入 `storage/remotion/renders`

## 5.10 服务端接入建议

### 5.10.1 Java 与 Remotion 的边界

本项目建议保持以下边界：

- Java 后端负责：
  - 生成标准化时间线 JSON
  - 管理任务状态
  - 决定 bundle / render 调度时机
  - 记录日志与渲染产物路径

- Node Remotion 负责：
  - bundle 组合
  - 渲染合成
  - 导出视频

### 5.10.2 推荐调用模式

推荐由 Java 调一个固定 Node 脚本：

```text
backend
  -> ProcessBuilder(node scripts/render.mjs --input timeline.json --output xxx.mp4)
  -> Node 侧读取 timeline JSON
  -> bundle / renderMedia
  -> 返回 exitCode + outputPath
```

不建议让 Java 直接拼 Remotion 的内部参数细节。Java 只应该传：

- 输入时间线文件路径
- 输出路径
- compositionId
- 渲染尺寸 / 帧率 / 时长

### 5.10.3 为什么不先做 HTTP 微服务

第一阶段不建议先单独拆出一个 Remotion 微服务：

- 现阶段目标是封装引擎，不是拆部署拓扑
- Node 脚本 + Java 调度已经足够支撑本地和单机环境
- 等到渲染任务量明显增大，再拆成渲染 Worker 更合理

## 5.11 推荐的首批封装边界

建议第一批只封装这 4 个能力：

1. **Composition 注册入口**
   - `frontend/remotion/index.ts`
2. **时间线到 props 的 DTO 适配**
   - 只吃后端标准时间线 JSON
3. **Node Bundle 脚本**
   - `scripts/bundle.mjs`
4. **Node Render 脚本**
   - `scripts/render.mjs`

先不要急着一开始就做：

- 多模板动态加载
- 分布式渲染
- 云存储回传
- 渲染队列中间件

## 5.12 风险与规避

### 5.12.1 依赖版本漂移

风险：

- `remotion`、`@remotion/renderer`、`@remotion/cli` 版本不一致会引发奇怪问题

约束：

- 所有 `@remotion/*` 包必须同版本锁定

### 5.12.2 浏览器缓存权限

风险：

- 服务端用户无权写浏览器缓存目录，导致渲染失败

约束：

- `REMOTION_BROWSER_CACHE` 必须指向可写目录

### 5.12.3 业务前端与渲染组合互相污染

风险：

- 把 Remotion 代码直接写进业务 `src/pages` 后，很容易出现依赖和构建脚本缠绕

约束：

- Remotion 组合独立放在 `frontend/remotion/`

### 5.12.4 产物目录失控

风险：

- bundle、temp、render 产物混放，后期难清理

约束：

- `storage/remotion/` 下按职责拆目录

## 5.13 本项目建议结论

综合当前仓库结构，建议采用以下口径：

1. **Remotion 工程放在 `frontend/` 内部，作为独立渲染子域**
2. **前端依赖中统一安装 `remotion + @remotion/player + @remotion/bundler + @remotion/renderer + @remotion/cli`**
3. **所有 Remotion 包必须固定为同一版本，不允许 `^` 漂移**
4. **Java 后端不直接实现渲染逻辑，只调 Node 渲染脚本**
5. **渲染缓存与产物统一放到 `storage/remotion/`**
6. **第一阶段先跑通 Studio、本地 render、Java 调度三件事**

## 5.14 下一步实施建议

当本文件确认后，下一步建议按以下顺序推进：

1. 在 `frontend/package.json` 中补 Remotion 依赖与脚本
2. 新建 `frontend/remotion/` 入口与最小 Composition
3. 新建 `frontend/scripts/render.mjs`，先跑通单 Composition 导出
4. 在后端新增 `RemotionRenderService`，只负责调 Node 脚本
5. 再将创作时间线 JSON 映射为 Remotion props

