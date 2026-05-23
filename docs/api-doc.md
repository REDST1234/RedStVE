# 批次 5：RESTful API 接口文档

## 5.1 通用约定

### 5.1.1 基础信息

| 项目 | 说明 |
|------|------|
| 协议 | HTTPS |
| 基础路径 | `/api/v1` |
| 字符编码 | UTF-8 |
| 请求体格式 | `application/json`，上传类接口使用 `multipart/form-data` |
| 响应体格式 | `application/json` |
| 认证方式 | Bearer Token (JWT)，请求头 `Authorization: Bearer <token>` |
| 日期格式 | ISO 8601 UTC（`2026-05-22T12:00:00.000Z`） |
| 分页参数 | `page`（页码，从 1 开始）、`size`（每页条数，默认 20，上限 100） |

### 5.1.2 统一响应结构

所有接口统一使用以下 JSON 响应结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1716220000000
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 业务状态码，`0` 表示成功，非 0 表示失败 |
| message | String | 状态描述信息 |
| data | Object / Array / null | 响应数据，可分页或为空 |
| timestamp | Long | 响应时间戳（毫秒） |

> [!NOTE]
> 本文档采用“HTTP 状态码表达协议语义 + `code` 表达业务结果”双轨约定：成功固定 `code=0`，失败返回非 0 业务码（示例中沿用与 HTTP 对齐的数值）。

**分页响应 data 结构：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [],
    "page": 1,
    "size": 20,
    "total": 150,
    "totalPages": 8
  },
  "timestamp": 1716220000000
}
```

**错误响应：**

```json
{
  "code": 404,
  "message": "模板不存在",
  "data": null,
  "timestamp": 1716220000000,
  "errorCode": "TEMPLATE_NOT_FOUND",
  "details": "template_id=xxx 在数据库中不存在"
}
```

### 5.1.3 错误码定义

#### HTTP 状态码

| HTTP 状态码 | 含义 | 触发场景 |
|-------------|------|----------|
| 200 | OK | 请求成功 |
| 201 | Created | 资源创建成功 |
| 202 | Accepted | 异步任务已受理 |
| 204 | No Content | 删除成功，无响应体 |
| 400 | Bad Request | 参数校验失败 |
| 401 | Unauthorized | 未认证 |
| 403 | Forbidden | 无权限 |
| 404 | Not Found | 资源不存在 |
| 409 | Conflict | 资源冲突（如重复创建） |
| 413 | Payload Too Large | 上传文件超过大小限制 |
| 415 | Unsupported Media Type | 不支持的视频/文件格式 |
| 422 | Unprocessable Entity | 业务逻辑异常（如素材不足无法迁移） |
| 429 | Too Many Requests | 请求频率超限 |
| 500 | Internal Server Error | 系统内部错误 |
| 503 | Service Unavailable | 服务繁忙（队列积压、LLM 限流） |

#### 业务错误码

| 错误码 | HTTP 状态 | 说明 |
|--------|-----------|------|
| `SUCCESS` | 200 | 操作成功（业务码固定为 `0`） |
| `BAD_REQUEST` | 400 | 通用参数错误 |
| `VIDEO_FORMAT_UNSUPPORTED` | 415 | 视频格式不支持 |
| `VIDEO_TOO_LARGE` | 413 | 视频文件过大 |
| `VIDEO_DURATION_EXCEEDED` | 400 | 视频时长超限 |
| `TASK_NOT_FOUND` | 404 | 拆解任务不存在 |
| `TASK_ALREADY_PROCESSED` | 409 | 任务已处理，不可重复操作 |
| `TEMPLATE_NOT_FOUND` | 404 | 模板不存在 |
| `TEMPLATE_NAME_DUPLICATE` | 409 | 模板名称重复 |
| `CATEGORY_NOT_FOUND` | 404 | 品类不存在 |
| `CATEGORY_ALREADY_EXISTS` | 409 | 品类已存在 |
| `MATERIAL_NOT_FOUND` | 404 | 素材不存在 |
| `MATERIAL_FORMAT_UNSUPPORTED` | 415 | 素材格式不支持 |
| `MIGRATION_NOT_FOUND` | 404 | 迁移任务不存在 |
| `GAP_UNFILLABLE` | 422 | 素材缺口无法补全 |
| `MATERIAL_INSUFFICIENT` | 422 | 素材数量不足以执行迁移 |
| `LLM_TIMEOUT` | 500 | LLM 调用超时 |
| `LLM_RESPONSE_INVALID` | 500 | LLM 返回格式非法 |
| `FFMPEG_ERROR` | 500 | FFmpeg 执行异常 |
| `ASR_ERROR` | 500 | 语音识别异常 |
| `INTERNAL_ERROR` | 500 | 系统内部错误 |
| `SERVICE_BUSY` | 503 | 服务繁忙 |

### 5.1.4 文件上传限制

| 限制项 | 值 | 说明 |
|--------|-----|------|
| 最大视频文件 | 500 MB | 单文件上传上限 |
| 支持视频格式 | mp4, mov, avi, webm | — |
| 视频时长范围 | 5~300 秒 | 过短无法拆解，过长分析成本高 |
| 最大样例视频数 | 5 条 | 单次拆解最多上传 5 条样例 |
| 支持图片格式 | jpg, png, webp | 素材上传 |
| 支持文案输入 | text/plain 或表单文本 | 创作素材上传 |

---

## 5.2 接口清单总览

```
/api/v1
│
├── /videos                             视频拆解（VideoAnalysisController）
│   ├── POST   /upload                  上传样例视频，创建拆解任务
│   ├── POST   /upload/batch            批量上传样例视频
│   ├── DELETE /materials/{materialBizId} 删除拆解素材（逻辑删+物理删）
│   ├── GET    /tasks/{taskId}          查询拆解任务状态
│   ├── GET    /tasks/{taskId}/progress SSE 流式推送任务进度
│   ├── GET    /tasks/{taskId}/result   查询拆解分析结果
│   ├── DELETE /tasks/{taskId}          取消/删除拆解任务
│   └── POST   /tasks/{taskId}/retry    重试失败的拆解任务
│
├── /templates                          结构模板（TemplateController）
│   ├── POST   /                        创建模板（人工）
│   ├── POST   /extract                 从分析结果提取模板
│   ├── GET    /                        模板列表（支持筛选）
│   ├── GET    /{templateId}            模板详情
│   ├── PUT    /{templateId}            全量更新模板
│   ├── PATCH  /{templateId}            部分更新模板
│   ├── DELETE /{templateId}            归档模板
│   ├── POST   /{templateId}/publish    发布模板
│   └── POST   /{templateId}/clone      克隆模板
│
├── /categories                         品类知识库（CategoryController）
│   ├── GET    /                        品类列表
│   ├── GET    /{categoryId}            品类详情
│   ├── POST   /                        人工注册品类
│   ├── PUT    /{categoryId}            更新品类知识
│   ├── PATCH  /{categoryId}/review     审核 LLM 自动发现的品类
│   └── POST   /{categoryId}/merge      合并品类
│
├── /materials                          用户素材（MaterialController）
│   ├── POST   /upload                  上传素材
│   ├── GET    /                        素材列表
│   ├── GET    /{materialId}            素材详情
│   ├── DELETE /{materialId}            删除素材
│   └── GET    /{materialId}/probe      探测素材元信息
│
├── /migrations                         结构迁移（MigrationController）
│   ├── POST   /                        创建迁移任务
│   ├── GET    /{migrationId}           查询迁移任务状态
│   ├── GET    /{migrationId}/timeline  查询编排时间线
│   ├── GET    /{migrationId}/gaps      查询素材缺口分析
│   ├── POST   /{migrationId}/fill      补全素材缺口
│   └── POST   /{migrationId}/compose   执行结构编排
│
└── /system                             系统接口
    ├── GET    /health                  健康检查
    └── GET    /stats                   系统统计
```

---

## 5.3 视频拆解接口（VideoAnalysisController）

### 5.3.1 POST /api/v1/videos/upload — 上传样例视频创建拆解任务

**描述：** 上传一条样例视频并立即完成 FFprobe 探测。系统返回 `taskId + materialBizId + mediaInfo`，
并将探测结果写入 `analysis_result_core`（仅核心探测字段，其他分析字段留空/默认）。
该接口的素材仅落库到 `analysis_video_material`（拆解流视频素材库），不进入创作素材库。

**请求：**

```
POST /api/v1/videos/upload
Content-Type: multipart/form-data
```

| 参数 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| file | body | File | 是 | 视频文件 |
| categoryHint | body | String | 否 | 品类提示（帮助 LLM 缩小分析范围） |
| priority | body | Integer | 否 | 任务优先级 1~10，默认 5 |
| projectId | body | String | 否 | 拆解项目 ID（`prj_xxx`），传入后自动绑定到项目素材关联表 |

**请求示例（curl）：**
```bash
curl -X POST https://api.example.com/api/v1/videos/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@sample_video.mp4" \
  -F "categoryHint=marketing" \
  -F "priority=3"
```

**响应（200 OK）：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "materialBizId": 739201238812300001,
    "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "status": "COMPLETED",
    "estimatedDuration": 15,
    "mediaInfo": {
      "duration": 14.566,
      "width": 458,
      "height": 812,
      "fps": 30.0,
      "codec": "h264",
      "bitrate": 1324000,
      "hasAudio": true,
      "audioCodec": "aac",
      "format": "mov,mp4,m4a,3gp,3g2,mj2"
    }
  },
  "timestamp": 1716220000000
}
```

| 响应字段 | 类型 | 说明 |
|----------|------|------|
| materialBizId | Long | 素材业务主键（雪花 ID） |
| taskId | String | 任务唯一标识（UUID v4） |
| status | String | 当前探测状态（P0 固定 `COMPLETED`） |
| estimatedDuration | Integer | 预估处理耗时（秒） |
| mediaInfo | Object | FFprobe 探测结果 |

---

### 5.3.2 POST /api/v1/videos/upload/batch — 批量上传样例视频

**描述：** 上传多条样例视频（上限 5 条）。P0 采用“每视频一任务”建模：每个文件创建独立 `taskId`，后续融合在模板提取阶段进行。
批量上传文件同样仅落库到 `analysis_video_material`。

**请求：**

```
POST /api/v1/videos/upload/batch
Content-Type: multipart/form-data
```

| 参数 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| files | body | File[] | 是 | 视频文件列表（最多 5 个） |
| categoryHint | body | String | 否 | 品类提示 |
| priority | body | Integer | 否 | 任务优先级 |
| projectId | body | String | 否 | 拆解项目 ID（`prj_xxx`），传入后每个文件都绑定到该项目 |

**响应（200 OK）：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskIds": [
      "a1b2c3d4-e5f6-7890-abcd-ef1234567801",
      "a1b2c3d4-e5f6-7890-abcd-ef1234567802",
      "a1b2c3d4-e5f6-7890-abcd-ef1234567803"
    ],
    "fileCount": 3,
    "status": "COMPLETED",
    "estimatedDuration": 120,
    "items": [
      {
        "materialBizId": 739201238812300001,
        "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567801",
        "fileName": "a.mp4",
        "mediaInfo": {
          "duration": 14.566,
          "width": 458,
          "height": 812,
          "fps": 30.0,
          "codec": "h264",
          "bitrate": 1324000,
          "hasAudio": true,
          "audioCodec": "aac",
          "format": "mov,mp4,m4a,3gp,3g2,mj2"
        }
      }
    ]
  },
  "timestamp": 1716220000000
}
```

**错误示例：**
```json
{
  "code": 400,
  "message": "批量上传文件数量超限，最多 5 条",
  "data": null,
  "timestamp": 1716220000000,
  "errorCode": "BAD_REQUEST"
}
```

---

### 5.3.3 GET /api/v1/videos/tasks/{taskId} — 查询拆解任务状态

**描述：** 轮询查询任务的当前状态与进度。

**路径参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| taskId | String | 任务 ID |

**响应（200 OK）：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "status": "ANALYZING",
    "progress": 65,
    "progressStep": "LLM 节奏结构分析中...",
    "sourceFileName": "sample_video.mp4",
    "createdAt": "2026-05-22T10:00:00.000Z",
    "updatedAt": "2026-05-22T10:01:30.000Z",
    "completedAt": null,
    "errorMessage": null
  },
  "timestamp": 1716220000000
}
```

| 响应字段 | 类型 | 说明 |
|----------|------|------|
| status | String | PENDING / PROCESSING / ANALYZING / COMPLETED / FAILED / CANCELED |
| progress | Integer | 进度 0~100 |
| progressStep | String | 当前步骤中文描述 |
| sourceFileName | String | 原始文件名 |
| errorMessage | String \| null | 失败时的错误信息 |
| partialFailedDimensions | String[] \| null | LLM 局部失败维度（如 `["rhythm"]`） |

---

### 5.3.8 DELETE /api/v1/videos/materials/{materialBizId} — 删除拆解素材

**描述：** 全局删除拆解素材。执行顺序为：
1) `analysis_video_material` 逻辑删除（`status=DELETED`，写 `deleted_at`）；
2) 清理 `deconstruct_project_material` 关联；
3) 物理删除磁盘文件。  
若物理删除失败，接口返回失败并回滚数据库变更。

**路径参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| materialBizId | Long | 素材业务主键 |

**响应（200 OK）：**

```json
{
  "code": 0,
  "message": "success",
  "data": true,
  "timestamp": 1716220000000
}
```

---

### 5.3.4 GET /api/v1/videos/tasks/{taskId}/progress — SSE 进度推送

**描述：** 通过 Server-Sent Events 实时推送任务进度，替代轮询方式。

**响应（SSE 事件流）：**

```
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
X-Accel-Buffering: no
```

```json
id: 1
event: progress
data: {"taskId":"xxx","status":"PROCESSING","progress":20,"progressStep":"FFmpeg 镜头切分中..."}

id: 2
event: progress
data: {"taskId":"xxx","status":"PROCESSING","progress":40,"progressStep":"ASR 语音转写中..."}

id: 3
event: progress
data: {"taskId":"xxx","status":"ANALYZING","progress":60,"progressStep":"LLM 脚本结构分析中..."}

id: 4
event: progress
data: {"taskId":"xxx","status":"ANALYZING","progress":80,"progressStep":"LLM 节奏结构分析中..."}

id: 5
event: complete
data: {"taskId":"xxx","status":"COMPLETED","progress":100,"progressStep":"拆解完成"}

event: heartbeat
data: {"ts":"2026-05-22T10:01:00.000Z"}
```

**错误事件：**
```json
event: error
data: {"taskId":"xxx","status":"FAILED","progress":45,"progressStep":"LLM 调用超时","errorMessage":"StructureAnalyzer 请求超时 (30s)"}
```

**SSE 协议约定：**
- 客户端断线重连需带 `Last-Event-ID`
- 服务端至少每 15s 发送一次 `heartbeat`，防止网关空闲断开
- 发送 `complete` 或 `error` 后，服务端主动关闭连接

---

### 5.3.5 GET /api/v1/videos/tasks/{taskId}/result — 查询拆解分析结果

**描述：** 任务完成（COMPLETED）后，获取完整的分析结果。

**Query 参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| includeTimeline | boolean | 否 | false | 是否加载时序冷数据（timeline/script/rhythm/packaging） |

**响应（200 OK）：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "videoInfo": {
      "duration": 30.5,
      "width": 1080,
      "height": 1920,
      "fps": 30,
      "codec": "h264",
      "bitrate": 4500000,
      "hasAudio": true,
      "audioCodec": "aac"
    },
    "shots": [
      {
        "shotIndex": 0,
        "startTime": 0.0,
        "endTime": 3.2,
        "duration": 3.2,
        "sceneScore": 0.47,
        "shotType": "face_closeup"
      },
      {
        "shotIndex": 1,
        "startTime": 3.2,
        "endTime": 12.0,
        "duration": 8.8,
        "sceneScore": 0.31,
        "shotType": "product_display"
      }
    ],
    "transcript": [
      { "text": "你有没有发现", "start": 0.5, "end": 1.8 },
      { "text": "这款产品用了之后", "start": 2.0, "end": 3.5 }
    ],
    "timelineLog": [
      {
        "time": "0.0s - 3.2s",
        "shotIndex": 0,
        "type": "SHOT+ASR",
        "content": "[镜头0] sceneScore=0.47 | ASR: '大一早八顶不住？'"
      }
    ],
    "structure": {
      "script": {
        "totalSegments": 4,
        "segments": [
          {
            "segmentIndex": 0,
            "role": "hook",
            "label": "开头钩子",
            "description": "用提问方式引发共鸣",
            "durationRange": { "min": 2, "max": 4 }
          }
        ]
      },
      "rhythm": {
        "overallPace": "medium",
        "avgShotDuration": 2.8,
        "climaxPosition": { "startPercent": 65, "endPercent": 80 }
      },
      "packaging": {
        "subtitleStyle": {
          "position": "bottom_center",
          "fontSize": "medium",
          "color": "#FFFFFF",
          "animation": "fade_in"
        }
      }
    },
    "categoryId": "marketing",
    "partialFailedDimensions": ["rhythm"],
    "analyzedAt": "2026-05-22T10:02:00.000Z"
  },
  "timestamp": 1716220000000
}
```

---

### 5.3.6 DELETE /api/v1/videos/tasks/{taskId} — 取消/删除拆解任务

**描述：** 仅允许取消 `PENDING` 任务（状态置为 `CANCELED`）；对于 `COMPLETED/FAILED/CANCELED` 任务执行历史删除（删除任务记录及关联文件）。

**响应（204 No Content）：** 无响应体。

**响应（409 Conflict——任务正在处理中无法取消）：**
```json
{
  "code": 409,
  "message": "任务正在处理中，无法取消",
  "data": null,
  "timestamp": 1716220000000,
  "errorCode": "TASK_ALREADY_PROCESSED"
}
```

---

### 5.3.7 POST /api/v1/videos/tasks/{taskId}/retry — 重试失败的拆解任务

**描述：** 重新提交失败（FAILED）的任务。重试上限 3 次。

**响应（202 Accepted）：**
```json
{
  "code": 0,
  "message": "任务已重新提交",
  "data": {
    "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "status": "PENDING",
    "retryCount": 2
  },
  "timestamp": 1716220000000
}
```

---

### 5.3.8 DTO ↔ DB 字段映射（P0）

| API 字段 | DB 字段/来源 | 说明 |
|----------|--------------|------|
| `taskId` | `video_analysis_task.task_id` | 任务主标识 |
| `status` | `video_analysis_task.status` | 含 `CANCELED` |
| `progress` | `video_analysis_task.progress` | 0~100 |
| `progressStep` | `video_analysis_task.progress_step` | 当前步骤 |
| `sourceFileName` | `video_analysis_task.source_file_path` 派生 | 取路径末尾文件名 |
| `videoInfo` | `analysis_result_core` 结构化列聚合 | 原始探测信息（组装返回） |
| `timelineLog` | `analysis_result_timeline_asset.timeline_log_json` | 时序归一日志（按需加载） |
| `categoryId` | `analysis_result_core.category_id` | 品类标识 |
| `partialFailedDimensions` | `analysis_result_core.partial_failed_dimensions` | LLM 局部失败维度 |
| `shots[]` | `shot` 聚合 | 按 `task_id` + `shot_index` 排序 |
| `transcript[]` | `asr_segment` 聚合 | 按 `task_id` + `segment_index` 排序 |
| `llmAnalysis.script/rhythm/packaging` | `analysis_result_timeline_asset` 对应 JSON 字段 | 冷数据按需反序列化 |

---

### 5.3.9 拆解任务状态机（P0）

| 当前状态 | 触发动作 | 下一个状态 | 约束 |
|----------|----------|------------|------|
| `PENDING` | 消费者开始处理 | `PROCESSING` | 出队即转移 |
| `PENDING` | 用户取消 | `CANCELED` | 仅未消费任务可取消 |
| `PROCESSING` | FFmpeg/ASR 完成 | `ANALYZING` | 进入 LLM 阶段 |
| `PROCESSING` | 处理异常 | `FAILED` | 记录 `error_step` |
| `ANALYZING` | 全部维度成功 | `COMPLETED` | 正常完成 |
| `ANALYZING` | 部分维度失败 | `COMPLETED` | 在结果中写 `partialFailedDimensions` |
| `ANALYZING` | 全维度失败 | `FAILED` | 无可用结构输出 |
| `FAILED` | 用户重试 | `PENDING` | `retry_count <= 3` |

> `DELETE /videos/tasks/{taskId}` 是资源操作，不是状态迁移。它只负责取消或删除记录，不引入 `DELETED` 状态值。

## 5.4 模板管理接口（TemplateController）

### 5.4.1 POST /api/v1/templates — 创建模板（人工）

**描述：** 手动创建一个视频结构模板。

**请求体：**
```json
{
  "templateName": "我的营销爆款模板",
  "categoryId": "marketing",
  "meta": {
    "targetDuration": { "min": 25, "max": 35, "unit": "seconds" },
    "aspectRatio": "9:16",
    "resolution": { "width": 1080, "height": 1920 },
    "style": "快节奏产品展示",
    "description": "基于多条爆款视频手工提取"
  },
  "scriptStructure": {
    "totalSegments": 4,
    "segments": [
      {
        "segmentIndex": 0,
        "role": "hook",
        "label": "开头钩子",
        "description": "前3秒用数据+提问制造好奇心",
        "durationRange": { "min": 2, "max": 4 },
        "durationWeight": 0.12
      }
    ]
  },
  "rhythmStructure": {
    "overallPace": "fast",
    "avgShotDuration": 2.5
  }
}
```

**响应（201 Created）：**
```json
{
  "code": 0,
  "message": "模板创建成功",
  "data": {
    "templateId": "tpl-uuid-1234",
    "templateName": "我的营销爆款模板",
    "version": "1.0.0",
    "status": "DRAFT",
    "createdAt": "2026-05-22T10:10:00.000Z"
  },
  "timestamp": 1716220000000
}
```

---

### 5.4.2 POST /api/v1/templates/extract — 从分析结果提取模板

**描述：** 将视频拆解分析结果（LLM 输出的结构数据）转换为可复用的模板。此接口是拆解→模板的桥接点。

**请求体：**
```json
{
  "taskIds": [
    "a1b2c3d4-e5f6-7890-abcd-ef1234567801",
    "a1b2c3d4-e5f6-7890-abcd-ef1234567802"
  ],
  "templateName": "赛博探店反转模板",
  "isFusion": true
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| taskIds | String[] | 是 | 已完成的拆解任务 ID 列表（1~5 个） |
| templateName | String | 是 | 模板名称 |
| isFusion | Boolean | 否 | 是否为多样例融合；`taskIds.length > 1` 时应为 `true` |

**响应（201 Created）：**

返回完整的模板对象，包含从 analysis_result_core / analysis_result_timeline_asset + TimelineMatcher + LLM 分析结果中提取的 scriptStructure、rhythmStructure、packagingStructure、categoryExtensions 等全部字段。

```json
{
  "code": 0,
  "message": "模板提取成功",
  "data": {
    "templateId": "tpl-extracted-uuid",
    "templateName": "赛博探店反转模板",
    "version": "1.0.0",
    "categoryId": "discovered_cyber_store_review",
    "status": "DRAFT",
    "isFusion": false,
    "categoryExtensions": {
      "discoveredCategoryId": "discovered_cyber_store_review",
      "discoveredCategoryName": "赛博探店反转类",
      "dynamicExtensionFields": [
        {
          "fieldName": "dramaConflictLevel",
          "fieldType": "STRING",
          "fieldValue": "extreme_reverse",
          "description": "短剧式矛盾冲突评级"
        }
      ],
      "discoveredPromptOverrides": {
        "scriptAnalysis": "把50%权重放在反转剧本的台词张力上",
        "rhythmAnalysis": "重点关注情绪反转节点的镜头切换密度变化"
      }
    },
    "createdAt": "2026-05-22T10:15:00.000Z"
  },
  "timestamp": 1716220000000
}
```

> [!IMPORTANT]
> 此接口触发热注册流程：如果 LLM 判定的品类在 `category_knowledge` 表中不存在，系统会自动执行 `INSERT` 创建新品类记录。

---

### 5.4.3 GET /api/v1/templates — 模板列表

**描述：** 分页查询模板，支持多条件筛选。

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 20，上限 100 |
| categoryId | String | 否 | 按品类筛选 |
| status | String | 否 | 按状态筛选：DRAFT / PUBLISHED / ARCHIVED |
| keyword | String | 否 | 按模板名称模糊搜索 |
| sortBy | String | 否 | 排序字段：createdAt / updatedAt / usageCount，默认 createdAt |
| sortOrder | String | 否 | 排序方式：asc / desc，默认 desc |
| isFusion | Boolean | 否 | 是否仅查融合模板 |

**响应（200 OK）：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "templateId": "tpl-uuid-1234",
        "templateName": "营销爆款模板",
        "categoryId": "marketing",
        "categoryName": "营销类",
        "version": "1.0.0",
        "status": "PUBLISHED",
        "isFusion": false,
        "schemaVersion": "v2",
        "usageCount": 42,
        "meta": {
          "targetDuration": { "min": 25, "max": 35, "unit": "seconds" },
          "aspectRatio": "9:16",
          "style": "快节奏产品展示"
        },
        "createdAt": "2026-05-22T10:10:00.000Z",
        "updatedAt": "2026-05-22T15:00:00.000Z"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 150,
    "totalPages": 8
  },
  "timestamp": 1716220000000
}
```

---

### 5.4.4 GET /api/v1/templates/{templateId} — 模板详情

**描述：** 获取模板完整详情，包含镜头（template_shot）和爆款因子（viral_factor）列表。

**响应（200 OK）：**

返回模板完整 JSON，结构与 [03-结构模板与知识库模块 §3.4](./03-结构模板与知识库模块.md#34-通用-json-模板-schema-v2) 中的 Schema v2 定义一致，外加 `status`、`usageCount` 等管理字段。

---

### 5.4.5 PUT /api/v1/templates/{templateId} — 全量更新模板

**描述：** 替换模板全部内容，版本号自动 +1。

**请求体：** 与 POST /api/v1/templates 创建模板请求体结构一致。

**注意：** `category` 字段不可变更（品类归属一旦确定不可修改）。

---

### 5.4.6 PATCH /api/v1/templates/{templateId} — 部分更新模板

**描述：** 仅更新指定字段。

**请求体：**
```json
{
  "templateName": "更新后的模板名称",
  "status": "PUBLISHED",
  "scriptStructure.script.totalSegments": 5
}
```

支持点号分隔的嵌套路径更新，如 `scriptStructure.segments[0].durationRange.min`。

---

### 5.4.7 DELETE /api/v1/templates/{templateId} — 归档模板

**描述：** 将模板状态标记为 ARCHIVED（软删除），不物理删除数据。

```json
{
  "code": 0,
  "message": "模板已归档",
  "data": {
    "templateId": "tpl-uuid-1234",
    "status": "ARCHIVED",
    "updatedAt": "2026-05-22T16:00:00.000Z"
  },
  "timestamp": 1716220000000
}
```

---

### 5.4.8 POST /api/v1/templates/{templateId}/publish — 发布模板

**描述：** 将草稿（DRAFT）模板发布为 PUBLISHED 状态。

**限制：** 模板必须包含完整的 segments 和 shots 定义。

---

### 5.4.9 POST /api/v1/templates/{templateId}/clone — 克隆模板

**描述：** 基于现有模板创建副本，方便二次编辑。

**请求体：**
```json
{
  "templateName": "营销爆款模板 - 变体B"
}
```

**响应（201 Created）：**
```json
{
  "code": 0,
  "message": "模板克隆成功",
  "data": {
    "templateId": "tpl-cloned-uuid",
    "templateName": "营销爆款模板 - 变体B",
    "version": "1.0.0",
    "clonedFrom": "tpl-uuid-1234",
    "status": "DRAFT",
    "createdAt": "2026-05-22T17:00:00.000Z"
  },
  "timestamp": 1716220000000
}
```

---

## 5.5 品类知识库接口（CategoryController）

### 5.5.1 GET /api/v1/categories — 品类列表

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |
| discoveredByLlm | Boolean | 否 | 按来源筛选：null=全部 / true=LLM 自动发现 / false=种子预置 |
| reviewStatus | String | 否 | 按审核状态筛选：APPROVED / PENDING_REVIEW / REJECTED |
| sortBy | String | 否 | 排序：usageCount / confidenceScore / createdAt |

**响应（200 OK）：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "categoryId": "marketing",
        "categoryName": "营销类",
        "discoveredByLlm": false,
        "confidenceScore": 0.95,
        "usageCount": 328,
        "reviewStatus": "APPROVED",
        "dynamicFieldCount": 5,
        "lastEvolvedAt": "2026-05-20T12:00:00.000Z",
        "createdAt": "2026-01-01T00:00:00.000Z"
      },
      {
        "categoryId": "discovered_cyber_store_review",
        "categoryName": "赛博探店反转类",
        "discoveredByLlm": true,
        "confidenceScore": 0.65,
        "usageCount": 3,
        "reviewStatus": "PENDING_REVIEW",
        "dynamicFieldCount": 3,
        "lastEvolvedAt": "2026-05-22T10:15:00.000Z",
        "createdAt": "2026-05-22T10:15:00.000Z"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 12,
    "totalPages": 1
  },
  "timestamp": 1716220000000
}
```

---

### 5.5.2 GET /api/v1/categories/{categoryId} — 品类详情

**响应（200 OK）：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "categoryId": "marketing",
    "categoryName": "营销类",
    "discoveredByLlm": false,
    "confidenceScore": 0.95,
    "usageCount": 328,
    "reviewStatus": "APPROVED",
    "dynamicFields": [
      {
        "fieldName": "hookType",
        "fieldType": "STRING",
        "fieldValue": "question",
        "description": "钩子类型"
      },
      {
        "fieldName": "hookOptions",
        "fieldType": "ARRAY",
        "fieldValue": ["question","pain_point","data_shock","controversy"],
        "description": "可选钩子策略池"
      },
      {
        "fieldName": "sellingPointCount",
        "fieldType": "NUMBER",
        "fieldValue": 3,
        "description": "推荐卖点数量"
      },
      {
        "fieldName": "ctaType",
        "fieldType": "STRING",
        "fieldValue": "click_link",
        "description": "行动号召类型"
      },
      {
        "fieldName": "socialProofType",
        "fieldType": "STRING",
        "fieldValue": "review_count",
        "description": "社会认同方式"
      }
    ],
    "promptOverrides": {
      "scriptAnalysis": "...",
      "rhythmAnalysis": "...",
      "packagingAnalysis": "..."
    },
    "fieldHistory": [
      {
        "fieldName": "socialProofType",
        "action": "ADDED",
        "sourceTaskId": "task-uuid-xxx",
        "changedAt": "2026-05-20T12:00:00.000Z"
      }
    ],
    "templateCount": 85,
    "lastEvolvedAt": "2026-05-20T12:00:00.000Z",
    "createdAt": "2026-01-01T00:00:00.000Z",
    "updatedAt": "2026-05-20T12:00:00.000Z"
  },
  "timestamp": 1716220000000
}
```

**响应（404 Not Found）：**
```json
{
  "code": 404,
  "message": "品类不存在",
  "data": null,
  "timestamp": 1716220000000,
  "errorCode": "CATEGORY_NOT_FOUND"
}
```

---

### 5.5.3 POST /api/v1/categories — 人工注册品类

**描述：** 开发者或运营人员手动注册一个新品类（区别于 LLM 自动发现）。

**请求体：**
```json
{
  "categoryId": "tutorial",
  "categoryName": "教程类",
  "dynamicFields": [
    {
      "fieldName": "difficultyLevel",
      "fieldType": "STRING",
      "fieldValue": "beginner",
      "description": "教程难度等级"
    },
    {
      "fieldName": "stepCount",
      "fieldType": "NUMBER",
      "fieldValue": 3,
      "description": "教程步骤数量"
    }
  ],
  "promptOverrides": {
    "scriptAnalysis": "重点关注步骤的清晰度和递进关系",
    "rhythmAnalysis": "关注每个步骤的节奏分配",
    "packagingAnalysis": "关注文字标注和步骤编号的展示"
  }
}
```

**响应（201 Created）：**

---

### 5.5.4 PUT /api/v1/categories/{categoryId} — 更新品类知识

**描述：** 更新品类知识（添加扩展字段、修改 Prompt 策略等）。增量合并语义：新增字段追加，已有字段更新值。

**注意：** 种子品类（`discoveredByLlm = false`）的 `categoryId` 不可变更。

---

### 5.5.5 PATCH /api/v1/categories/{categoryId}/review — 审核品类

**描述：** 运营人员审核 LLM 自动发现的品类。种子品类不可审核。

**请求体：**
```json
{
  "reviewStatus": "APPROVED",
  "reviewComment": "品类定义合理，通过审核"
}
```

---

### 5.5.6 POST /api/v1/categories/{categoryId}/merge — 合并品类

**描述：** 当发现两个品类高度相似时，将源品类合并到目标品类。

**请求体：**
```json
{
  "targetCategoryId": "marketing",
  "action": "merge_fields"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| targetCategoryId | String | 是 | 合并目标品类 ID |
| action | String | 是 | merge_fields：合并扩展字段并集；replace：完全替换 |

**响应：**
```json
{
  "code": 0,
  "message": "品类合并成功",
  "data": {
    "mergedCategoryId": "marketing",
    "absorbedCategoryId": "marketing_v2",
    "mergedFieldCount": 8,
    "newUsageCount": 420
  },
  "timestamp": 1716220000000
}
```

---

## 5.6 创作素材管理接口（MaterialController）

> [!IMPORTANT]
> 本章节接口面向创作流，素材落库到 `creative_material`（文案/图片/视频）。与拆解流 `analysis_video_material` 物理隔离。

### 5.6.1 POST /api/v1/materials/upload — 上传素材

**请求：**

```
POST /api/v1/materials/upload
Content-Type: multipart/form-data
```

| 参数 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| materialType | body | String | 是 | 素材类型：VIDEO / IMAGE / TEXT |
| file | body | File | 否 | 素材文件（VIDEO/IMAGE 必填） |
| textContent | body | String | 否 | 文案内容（TEXT 必填） |
| tags | body | String | 否 | 用户标签（逗号分隔） |
| description | body | String | 否 | 素材描述 |

约束：`VIDEO/IMAGE` 必须上传 `file`；`TEXT` 必须传 `textContent`。

**响应（201 Created）：**
```json
{
  "code": 0,
  "message": "素材上传成功",
  "data": {
    "materialId": "mat-uuid-5678",
    "materialType": "VIDEO",
    "fileName": "product_shot.mp4",
    "fileSize": 15728640,
    "duration": 5.2,
    "width": 1080,
    "height": 1920,
    "format": "mp4",
    "tags": ["产品特写", "手持镜头"],
    "status": "ACTIVE",
    "createdAt": "2026-05-22T11:00:00.000Z"
  },
  "timestamp": 1716220000000
}
```

---

### 5.6.2 GET /api/v1/materials — 素材列表

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |
| materialType | String | 否 | 类型筛选：VIDEO / IMAGE / TEXT |
| status | String | 否 | 状态筛选：ACTIVE / PROCESSING / DELETED |
| tag | String | 否 | 按标签筛选 |
| keyword | String | 否 | 按文件名/描述模糊搜索 |

**响应（200 OK）：** 分页响应，`list` 中包含素材摘要信息。

---

### 5.6.3 GET /api/v1/materials/{materialId} — 素材详情

**响应（200 OK）：** 返回素材完整信息，包含文件路径、元信息、标签等。

---

### 5.6.4 DELETE /api/v1/materials/{materialId} — 删除素材

**描述：** 逻辑删除（status = DELETED），不立即物理删除文件。

---

### 5.6.5 GET /api/v1/materials/{materialId}/probe — 探测素材元信息

**描述：** 对已上传的素材执行 FFmpeg probe，返回详细元信息。用于视频/音频素材的深度信息探测。

**响应（200 OK）：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "materialId": "mat-uuid-5678",
    "duration": 5.2,
    "width": 1080,
    "height": 1920,
    "fps": 30,
    "codec": "h264",
    "bitrate": 4500000,
    "hasAudio": true,
    "audioCodec": "aac",
    "sceneScore": 0.35
  },
  "timestamp": 1716220000000
}
```

---

## 5.7 结构迁移接口（MigrationController）

### 5.7.1 POST /api/v1/migrations — 创建迁移任务

**描述：** 基于一个已发布的模板，使用用户素材执行结构迁移编排。

**请求体：**
```json
{
  "sourceTemplateId": "tpl-uuid-1234",
  "materialIds": [
    "mat-uuid-111",
    "mat-uuid-222",
    "mat-uuid-333"
  ],
  "migrationOptions": {
    "preserveViralFactors": true,
    "targetDuration": 30,
    "aspectRatio": "9:16"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sourceTemplateId | String | 是 | 源模板 ID |
| materialIds | String[] | 是 | 用户选定的素材 ID 列表 |
| migrationOptions.preserveViralFactors | Boolean | 否 | 是否保留爆款因子建议 |
| migrationOptions.targetDuration | Integer | 否 | 目标视频时长（秒） |
| migrationOptions.aspectRatio | String | 否 | 目标画幅比例 |

**响应（202 Accepted）：**
```json
{
  "code": 0,
  "message": "迁移任务创建成功",
  "data": {
    "migrationId": "mig-uuid-9999",
    "sourceTemplateId": "tpl-uuid-1234",
    "status": "PENDING",
    "materialCount": 3,
    "estimatedDuration": 45
  },
  "timestamp": 1716220000000
}
```

---

### 5.7.2 GET /api/v1/migrations/{migrationId} — 查询迁移任务状态

**响应（200 OK）：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "migrationId": "mig-uuid-9999",
    "sourceTemplateId": "tpl-uuid-1234",
    "sourceTemplateName": "营销爆款模板",
    "status": "GAP_ANALYZING",
    "progress": 35,
    "materialCount": 3,
    "createdAt": "2026-05-22T12:00:00.000Z",
    "updatedAt": "2026-05-22T12:00:30.000Z"
  },
  "timestamp": 1716220000000
}
```

---

### 5.7.3 GET /api/v1/migrations/{migrationId}/gaps — 查询素材缺口分析

**描述：** 获取 GapDetector 分析后的素材缺口报告。

**响应（200 OK）：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "migrationId": "mig-uuid-9999",
    "templateSlotCount": 8,
    "filledSlotCount": 5,
    "gapCount": 3,
    "fillStrategy": "USER_UPLOAD",
    "gaps": [
      {
        "slotName": "shot_3_face_closeup",
        "slotType": "SHOT",
        "requiredDescription": "手持产品特写，展示质感和细节",
        "durationRequired": { "min": 2, "max": 3 },
        "suggestion": "建议上传产品近景特写视频"
      },
      {
        "slotName": "hook_text_overlay",
        "slotType": "TEXT",
        "requiredDescription": "钩子段落的文字覆盖层",
        "suggestion": "请提供标题文案（10字以内）"
      },
      {
        "slotName": "bgm_track",
        "slotType": "AUDIO",
        "requiredDescription": "BPM 110~140 的快节奏背景音乐",
        "durationRequired": { "min": 28, "max": 32 },
        "suggestion": "建议上传背景音乐或选择系统推荐曲库"
      }
    ],
    "createdAt": "2026-05-22T12:00:35.000Z"
  },
  "timestamp": 1716220000000
}
```

---

### 5.7.4 POST /api/v1/migrations/{migrationId}/fill — 补全素材缺口

**描述：** 用户根据缺口分析结果上传补充素材或提供文本内容。

**请求体：**
```json
{
  "fills": [
    {
      "slotName": "shot_3_face_closeup",
      "materialId": "mat-uuid-new-444"
    },
    {
      "slotName": "hook_text_overlay",
      "textContent": "早八人自救指南"
    },
    {
      "slotName": "bgm_track",
      "materialId": "mat-uuid-bgm-555"
    }
  ]
}
```

**响应（200 OK）：**
```json
{
  "code": 0,
  "message": "缺口补全成功",
  "data": {
    "migrationId": "mig-uuid-9999",
    "filledSlotCount": 8,
    "gapCount": 0,
    "allGapsFilled": true
  },
  "timestamp": 1716220000000
}
```

---

### 5.7.5 POST /api/v1/migrations/{migrationId}/compose — 执行结构编排

**描述：** 所有素材缺口补全后，触发 StructureMigrator + TimelineComposer 执行最终编排。

**前置条件：** gapCount = 0（所有槽位已填充）。

**响应（202 Accepted）：**
```json
{
  "code": 0,
  "message": "编排任务已启动",
  "data": {
    "migrationId": "mig-uuid-9999",
    "status": "COMPOSING"
  },
  "timestamp": 1716220000000
}
```

**错误（422 Unprocessable Entity——素材不足）：**
```json
{
  "code": 422,
  "message": "素材缺口尚未完全补全，无法执行编排",
  "data": {
    "remainingGaps": 2,
    "gaps": [
      { "slotName": "bgm_track", "slotType": "AUDIO" }
    ]
  },
  "timestamp": 1716220000000,
  "errorCode": "MATERIAL_INSUFFICIENT"
}
```

---

### 5.7.6 GET /api/v1/migrations/{migrationId}/timeline — 查询编排时间线

**描述：** 编排完成后（COMPLETED），获取 TimelineComposer 生成的完整时间线。

**响应（200 OK）：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "migrationId": "mig-uuid-9999",
    "sourceTemplateId": "tpl-uuid-1234",
    "totalDuration": 30.0,
    "tracks": [
      {
        "trackType": "VIDEO",
        "segments": [
          {
            "segmentIndex": 0,
            "role": "hook",
            "startTime": 0.0,
            "endTime": 3.5,
            "sourceMaterialId": "mat-uuid-111",
            "transitionIn": "fade",
            "transitionOut": "hard_cut"
          },
          {
            "segmentIndex": 1,
            "role": "display",
            "startTime": 3.5,
            "endTime": 15.0,
            "sourceMaterialId": "mat-uuid-222",
            "transitionIn": "hard_cut",
            "transitionOut": "dissolve"
          }
        ]
      },
      {
        "trackType": "AUDIO",
        "segments": [
          {
            "startTime": 0.0,
            "endTime": 30.0,
            "sourceMaterialId": "mat-uuid-bgm-555",
            "volume": 0.8
          }
        ]
      },
      {
        "trackType": "SUBTITLE",
        "entries": [
          {
            "startTime": 0.5,
            "endTime": 2.0,
            "text": "早八人自救指南",
            "style": {
              "position": "bottom_center",
              "fontSize": "medium",
              "color": "#FFFFFF",
              "animation": "fade_in"
            }
          }
        ]
      }
    ],
    "viralFactorHints": [
      {
        "factorName": "钩子策略",
        "appliedAt": "0.0s - 3.5s",
        "note": "已应用反问+数据的钩子模式"
      }
    ],
    "composedAt": "2026-05-22T12:01:00.000Z"
  },
  "timestamp": 1716220000000
}
```

---

## 5.8 系统接口

### 5.8.1 GET /api/v1/system/health — 健康检查

**响应（200 OK）：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "UP",
    "components": {
      "mysql": { "status": "UP", "latencyMs": 2 },
      "redis": { "status": "UP", "latencyMs": 1 },
      "rabbitmq": { "status": "UP", "latencyMs": 3 },
      "llm": { "status": "UP", "latencyMs": 250 },
      "ffmpeg": { "status": "UP", "version": "6.1.0" }
    },
    "queueDepth": {
      "video.analysis.queue": 12,
      "material.process.queue": 5,
      "migration.compose.queue": 3
    }
  },
  "timestamp": 1716220000000
}
```

---

### 5.8.2 GET /api/v1/system/stats — 系统统计

**响应（200 OK）：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalTasks": 15230,
    "completedTasks": 14800,
    "failedTasks": 230,
    "totalTemplates": 850,
    "publishedTemplates": 320,
    "totalCategories": 25,
    "llmDiscoveredCategories": 18,
    "totalMigrations": 5600,
    "totalMaterials": 42000,
    "periodStats": {
      "last24h": {
        "tasksCreated": 120,
        "tasksCompleted": 115,
        "migrationsCompleted": 45,
        "avgTaskDuration": 52.3
      }
    }
  },
  "timestamp": 1716220000000
}
```

---

## 5.9 接口错误码速查表

| 接口路径 | 可能触发的错误码 |
|----------|-----------------|
| POST /videos/upload | VIDEO_FORMAT_UNSUPPORTED, VIDEO_TOO_LARGE, FFMPEG_ERROR, PROJECT_NOT_FOUND |
| POST /videos/upload/batch | 同上 + BAD_REQUEST（文件数超限） |
| DELETE /videos/materials/{materialBizId} | MATERIAL_NOT_FOUND, MATERIAL_DELETE_FAILED |
| GET /videos/tasks/{taskId} | TASK_NOT_FOUND |
| GET /videos/tasks/{taskId}/result | TASK_NOT_FOUND, TASK_ALREADY_PROCESSED（任务未完成） |
| DELETE /videos/tasks/{taskId} | TASK_NOT_FOUND, TASK_ALREADY_PROCESSED |
| POST /videos/tasks/{taskId}/retry | TASK_NOT_FOUND, TASK_ALREADY_PROCESSED（已达重试上限） |
| POST /templates | CATEGORY_NOT_FOUND, TEMPLATE_NAME_DUPLICATE |
| POST /templates/extract | TASK_NOT_FOUND, CATEGORY_ALREADY_EXISTS |
| GET /templates/{templateId} | TEMPLATE_NOT_FOUND |
| DELETE /templates/{templateId} | TEMPLATE_NOT_FOUND |
| POST /categories | CATEGORY_ALREADY_EXISTS |
| PATCH /categories/{categoryId}/review | CATEGORY_NOT_FOUND |
| POST /categories/{categoryId}/merge | CATEGORY_NOT_FOUND |
| POST /materials/upload | MATERIAL_FORMAT_UNSUPPORTED |
| POST /migrations | TEMPLATE_NOT_FOUND, MATERIAL_NOT_FOUND |
| GET /migrations/{migrationId}/gaps | MIGRATION_NOT_FOUND |
| POST /migrations/{migrationId}/fill | MIGRATION_NOT_FOUND, MATERIAL_NOT_FOUND |
| POST /migrations/{migrationId}/compose | MIGRATION_NOT_FOUND, MATERIAL_INSUFFICIENT, GAP_UNFILLABLE |

---

## 5.10 接口调用时序示例

### 5.10.1 完整用户旅程

```
用户                   前端                后端API                 Async Worker
 │                     │                    │                         │
 │  上传样例视频        │                    │                         │
 ├────────────────────►│                    │                         │
 │                     │  POST /videos/upload                        │
 │                     ├───────────────────►│                         │
 │                     │     200 {taskId}   │                         │
 │                     │◄───────────────────┤                         │
 │                     │                    │  @Async 提交拆解任务     │
 │                     │                    ├────────────────────────►│
 │                     │                    │                   FFmpeg+LLM
 │  轮询/SSE 进度       │                    │                         │
 ├────────────────────►│  GET /tasks/{taskId}/progress (SSE)          │
 │                     ├───────────────────►│                         │
 │                     │   SSE: progress 20%│◄────────────────────────┤
 │                     │◄───────────────────┤                         │
 │                     │   SSE: progress 60%│                         │
 │                     │◄───────────────────┤                         │
 │                     │   SSE: complete    │                         │
 │                     │◄───────────────────┤                         │
 │                     │                    │                         │
 │  查看拆解结果        │                    │                         │
 ├────────────────────►│  GET /tasks/{taskId}/result                  │
 │                     ├───────────────────►│                         │
 │                     │   200 分析结果JSON │                         │
 │                     │◄───────────────────┤                         │
 │                     │                    │                         │
 │  提取为模板          │                    │                         │
 ├────────────────────►│  POST /templates/extract                     │
 │                     ├───────────────────►│                         │
 │                     │   201 模板创建成功  │  品类热注册（如需）       │
 │                     │◄───────────────────┤                         │
 │                     │                    │                         │
 │  上传自己的素材      │                    │                         │
 ├────────────────────►│  POST /materials/upload (×N)                 │
 │                     ├───────────────────►│                         │
 │                     │   201 素材信息      │                         │
 │                     │◄───────────────────┤                         │
 │                     │                    │                         │
 │  创建迁移任务        │                    │                         │
 ├────────────────────►│  POST /migrations  │                         │
 │                     ├───────────────────►│                         │
 │                     │   202 {migrationId}│                         │
 │                     │◄───────────────────┤                         │
 │                     │                    │                         │
 │  查看缺口            │                    │                         │
 ├────────────────────►│  GET /migrations/{id}/gaps                   │
 │                     ├───────────────────►│                         │
 │                     │   200 gaps[]       │                         │
 │                     │◄───────────────────┤                         │
 │                     │                    │                         │
 │  补全素材            │                    │                         │
 ├────────────────────►│  POST /migrations/{id}/fill                  │
 │                     ├───────────────────►│                         │
 │                     │   200 allGapsFilled=true                     │
 │                     │◄───────────────────┤                         │
 │                     │                    │                         │
 │  执行编排            │                    │                         │
 ├────────────────────►│  POST /migrations/{id}/compose               │
 │                     ├───────────────────►│                         │
 │                     │   202 COMPOSING    │  @Async 提交编排任务      │
 │                     │◄───────────────────├────────────────────────►│
 │                     │                    │                  迁移编排
 │                     │                    │◄────────────────────────┤
 │  查看时间线          │                    │                         │
 ├────────────────────►│  GET /migrations/{id}/timeline               │
 │                     ├───────────────────►│                         │
 │                     │   200 完整时间线    │                         │
 │◄────────────────────┤◄───────────────────┤                         │
```

---

## 5.11 接口版本管理

### 5.11.1 URL 路径版本

当前版本：`/api/v1`

版本演进策略：
- 向后兼容的小改动：保持 v1，通过增加可选字段实现
- Breaking Change：新开 `/api/v2` 版本路径，v1 保持可用至少 6 个月过渡期
- Deprecation：在响应头中添加 `Sunset` 和 `Deprecation` 头通知客户端

### 5.11.2 响应头约定

| Header | 说明 |
|--------|------|
| `X-Request-Id` | 请求追踪 ID（用于日志关联） |
| `X-RateLimit-Remaining` | 当前时间窗口剩余请求次数 |
| `X-RateLimit-Reset` | 限流重置时间（Unix 时间戳） |
| `Deprecation` | `true` 表示该接口即将废弃 |
| `Sunset` | 接口废弃日期（ISO 8601） |

---

## 5.12 事件异步通知（Webhook / Async）

对于耗时可能超过 30s 的处理（如视频拆解、结构编排），除 SSE 推送外，还可通过以下方式通知：

### 5.12.1 Async 事件模型

当前 Demo/参赛模式默认移除 RabbitMQ，异步执行由应用内线程池承载：
- 通过 `@EnableAsync + ThreadPoolTaskExecutor(videoTaskExecutor)` 提交任务
- 任务状态与进度仍落 `video_analysis_task + Redis`，前端继续通过 SSE/轮询获取
- 需要对外通知时使用 Webhook（见 5.12.2）

### 5.12.2 Webhook 回调（可选扩展）

若需要主动通知外部系统，支持配置 Webhook URL：

```json
POST <webhook_url>
{
  "event": "task.completed",
  "data": {
    "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "status": "COMPLETED",
    "completedAt": "2026-05-22T10:02:00.000Z"
  }
}
```

