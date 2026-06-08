# RedStVE (Red Structure Video Engine)

RedStVE 是一个视频结构提取与重组系统。系统包含视频结构解析、素材匹配编排以及基于代码驱动的视频渲染功能。主要流程包括：提取参考视频的元数据（分镜时长、转场、文本）、用户上传或生成新素材、以及通过渲染服务合成为新视频。

## 系统架构

系统包含以下主要服务模块：

- **Backend (Java Spring Boot)**：处理业务逻辑、任务编排，并集成 Spring AI 用于与外部 LLM 交互。
- **Frontend (React + Vite)**：提供用户界面，包括拆解看板、工作流页面及数据图谱展示。
- **Remotion Service (Node.js)**：独立的视频渲染服务。通过无头浏览器 (Chromium) 将传入的 `CompositionScript` 渲染为 MP4 文件。
- **Vector Database (ChromaDB)**：存储背景音乐等数据的特征向量，支持相似度检索。
- **AI 节点支持 (Ollama, ComfyUI)**：依赖本地部署的 Ollama 处理文本嵌入向量，依赖 ComfyUI 节点进行图像预处理（如背景移除）。

## 渲染机制 (Remotion)

本系统在组装和导出视频阶段使用了 Remotion：
1. **基于组件的视频定义**：图层、字幕、转场等元素由 React 组件描述。
2. **后台无头渲染**：Node.js 服务接收 JSON 格式的脚本结构，通过 `@remotion/renderer` 在后台进行视频帧捕获和合成。

---

## 本地部署

项目提供 `docker-compose.yml` 用于本地环境部署。

### 前置要求
- 安装 Docker 和 Docker Compose。
- 确保系统可用端口：`80` (Web), `8080` (Backend API), `3001` (Remotion Service), `3306` (MySQL), `6379` (Redis), `8000` (Chroma)。

### 1. 启动容器编排环境

在项目根目录运行以下命令进行构建和启动：

```bash
docker-compose up -d --build
```

该命令将启动以下容器：
- `mysql`, `redis`, `chroma`
- `backend` (Spring Boot API 服务)
- `remotion` (Node.js 渲染服务)
- `frontend` (Nginx 代理，映射到 80 端口)

系统启动完成后，访问 `http://localhost` 即可进入前端页面。

### 2. 宿主机 AI 依赖配置

部分 AI 推理服务需在宿主机直接运行，Docker 容器会通过 `host.docker.internal` 与其通信。

#### Ollama 配置
系统使用 `nomic-embed-text` 模型进行文本向量化：
1. 启动 Ollama 服务。
2. 拉取所需模型：
```bash
ollama run nomic-embed-text
```

#### ComfyUI 配置
用于处理涉及像素级修改的图像任务。请注意，**系统并未硬编码 ComfyUI 端口**，默认通过环境变量或配置文件指向您的服务地址。
1. 启动 ComfyUI（通常默认运行在 `8188` 端口）。
2. 本项目重度依赖背景移除节点，请务必使用 **ComfyUI Manager** 搜索并安装 **ComfyUI-RMBG** 插件。
3. 如果您的 ComfyUI 端口不是 `8188`，或者在非宿主机环境运行，可在 `.env` 配置文件或 `docker-compose.yml` 中修改 `COMFYUI_BASE_URL` 环境变量以正确映射服务。

## 目录结构说明

`/storage` 目录用于存放系统运行时的各种文件：
- `/storage/audio-database/`：基础音频文件（已由版本控制记录）。
- `/storage/analysis-video/` 及 `/storage/analysis-audio/`：上传的视频和拆解过程中生成的中间产物（已在 .gitignore 中忽略）。
