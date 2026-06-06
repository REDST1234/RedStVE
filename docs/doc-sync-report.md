# RedStVE 文档不同步复盘报告

日期：2026-06-07

## 1. 背景
本项目在演进过程中，经历了从“基于短视频样例的结构分析与提取”到“全面生成动态视频”的大幅迭代。在此过程中，旧有文档（如最初的 `project_doc.txt` 与 `CLAUDE.md`）已不能完全反映系统当前的架构和功能状态。
为了给后续的维护和二次开发提供准确的参考，特制定此复盘报告。

## 2. 核心偏离点与架构更新

### 2.1 项目定位与品牌视觉
- **旧状态**：原名为 `VideoDecon`，主要聚焦于“视频拆解（Deconstruct）”，前端页面风格较为原始，依赖基础组件。
- **当前状态**：项目已更名为 **RedStVE**。前端引入了统一的“暗夜科技（Midnight Dark Tech）”及高对比度浅色玻璃拟物风格主题，启用了自定义设计的 R 字几何 Logo。

### 2.2 前端技术栈的演进
- **旧状态**：`CLAUDE.md` 记载 `No routing library configured yet — bare React setup`。
- **当前状态**：深度集成了 `react-router-dom` 路由系统。页面划分为三大核心流：
  - `/deconstruct/*`（拆解看板与详情流）
  - `/create/*`（创作项目流，多级标签导航）
  - `/bgm-knowledge`（音乐知识图谱与向量展示）

### 2.3 核心渲染能力（Remotion 的引入）
- **旧状态**：系统仅包含 Java 后端与 Vite 前端，主要依赖云端或 FFmpeg 预处理。
- **当前状态**：引入了全新的第三方服务层 —— **`remotion-service`**。
  - 这是基于 Node.js 和 Remotion 搭建的动态视频渲染引擎。
  - 通过暴露在 3001 端口的 API 接收后端下发的 `CompositionScript`。
  - 利用无头浏览器 (Chromium) 与 `ffmpeg` 将基于 React 编写的动态视频结构渲染为高品质 MP4。

### 2.4 底层存储与架构耦合
- **状态同步**：现在的后台强依赖于 `ChromaDB`（用于背景音乐和视频特征的向量检索）、`Redis`（作为 Remotion 渲染进度的实时通讯管道和缓存层）以及外部的生成式 AI 服务（Ollama, ComfyUI）。

## 3. 应对策略
1. **废弃与修改**：全面重写 `README.md`，使用 `docker-compose` 收敛复杂的环境依赖，消除新手环境配置痛点。
2. **规范化**：后续功能的增加，需同时更新 `README.md` 的架构描述。
