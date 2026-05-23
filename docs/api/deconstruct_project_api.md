# 拆解流项目联调 API 文档 (Deconstruct Project API)

**基础路径**: `/api/v1/deconstruct/projects`

## 1. 新建项目
**POST** `/api/v1/deconstruct/projects`

### 1.1 请求 (Request)
**Content-Type**: `application/json`

```json
{
  "title": "B站百大UP主影视解说结构",
  "description": "分析前三分钟的黄金悬念设置，以及情绪曲线推进。",
  "tags": ["影视", "混剪", "从零"],
  "coverUrl": "https://example.com/cover.jpg"
}
```

### 1.2 响应 (Response)
**Status**: `200 OK`

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "prj_1234567890",
    "title": "B站百大UP主影视解说结构",
    "description": "分析前三分钟的黄金悬念设置，以及情绪曲线推进。",
    "tags": ["影视", "混剪", "从零"],
    "coverUrl": "https://example.com/cover.jpg",
    "status": "PENDING",
    "createdAt": "2026-05-23T12:00:00Z",
    "updatedAt": "2026-05-23T12:00:00Z"
  }
}
```

---

## 2. 更新项目 (保存)
**PUT** `/api/v1/deconstruct/projects/{id}`

### 2.1 请求参数 (Path Variables)
- `id` (String): 项目的唯一标识符。

### 2.2 请求体 (Request Body)
**Content-Type**: `application/json`

```json
{
  "title": "更新后的标题",
  "description": "更新后的描述",
  "tags": ["电商", "营销"],
  "coverUrl": "https://example.com/cover_new.jpg"
}
```

### 2.3 响应 (Response)
**Status**: `200 OK`

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "prj_1234567890",
    "title": "更新后的标题",
    "description": "更新后的描述",
    "tags": ["电商", "营销"],
    "coverUrl": "https://example.com/cover_new.jpg",
    "status": "PENDING",
    "createdAt": "2026-05-23T12:00:00Z",
    "updatedAt": "2026-05-23T12:05:00Z"
  }
}
```

---

## 3. 删除项目
**DELETE** `/api/v1/deconstruct/projects/{id}`

### 3.1 请求参数 (Path Variables)
- `id` (String): 需要删除的项目 ID。

### 3.2 响应 (Response)
**Status**: `200 OK`

```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

---

## 4. 获取项目详情
**GET** `/api/v1/deconstruct/projects/{id}`

### 4.1 请求参数 (Path Variables)
- `id` (String): 项目 ID。

### 4.2 响应 (Response)
**Status**: `200 OK`

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "prj_1234567890",
    "title": "B站百大UP主影视解说结构",
    "description": "分析前三分钟的黄金悬念设置，以及情绪曲线推进。",
    "tags": ["影视", "混剪", "从零"],
    "coverUrl": "https://example.com/cover.jpg",
    "status": "COMPLETED",
    "materials": [
      {
        "materialBizId": 739201238812300001,
        "taskId": "6fdd0ff1-7746-4ee7-be94-a90c2233ee07",
        "filePath": "D:/Develop/code/bytedance-ai-video/storage/analysis-video/a.mp4",
        "coverCandidate": "file:///D:/Develop/code/bytedance-ai-video/storage/analysis-video/a.mp4",
        "duration": 14.566,
        "width": 458,
        "height": 812,
        "format": "mp4",
        "createdAt": "2026-05-23T12:00:01Z"
      }
    ],
    "createdAt": "2026-05-23T12:00:00Z",
    "updatedAt": "2026-05-23T12:00:00Z"
  }
}
```

---

## 5. 获取项目列表
**GET** `/api/v1/deconstruct/projects`

### 5.1 查询参数 (Query Parameters)
- `page` (Int): 页码，默认 1。
- `size` (Int): 每页数量，默认 10。
- `keyword` (String, Optional): 搜索关键词（匹配标题或描述）。

### 5.2 响应 (Response)
**Status**: `200 OK`

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 100,
    "page": 1,
    "size": 10,
    "list": [
      {
        "id": "prj_1234567890",
        "title": "B站百大UP主影视解说结构",
        "description": "分析前三分钟的黄金悬念设置，以及情绪曲线推进。",
        "tags": ["影视", "混剪", "从零"],
        "coverUrl": "https://example.com/cover.jpg",
        "status": "COMPLETED",
        "createdAt": "2026-05-23T12:00:00Z",
        "updatedAt": "2026-05-23T12:00:00Z"
      }
    ]
  }
}
```

---

## 6. 上传样例视频并绑定拆解项目（新增）

> 上传接口位于 `/api/v1/videos`，此处仅说明与拆解项目联动的新增参数。

### 6.1 单文件上传绑定项目
**POST** `/api/v1/videos/upload`

`multipart/form-data` 新增可选参数：
- `projectId` (String, Optional): 拆解项目 ID（`prj_xxx`）。

当传入 `projectId` 时，后端会在上传成功后自动建立 `deconstruct_project_material` 关联记录，并回填本次 `taskId`。

### 6.2 批量上传绑定项目
**POST** `/api/v1/videos/upload/batch`

`multipart/form-data` 新增可选参数：
- `projectId` (String, Optional): 拆解项目 ID（`prj_xxx`）。

当传入 `projectId` 时，批量上传的每个文件都会建立一条项目素材关联记录。
