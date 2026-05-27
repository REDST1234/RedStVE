# Spring AI + Chroma + Ollama 本地联调指南

## 1. 启动 Chroma

```bash
docker run -d --name chroma -p 8000:8000 chromadb/chroma:latest
```

## 2. 启动 Ollama 并准备 Embedding 模型

```bash
ollama serve
ollama pull nomic-embed-text
```

## 3. 配置后端环境变量（`backend/.env`）

```properties
CHROMA_HOST=http://localhost
CHROMA_PORT=8000
CHROMA_TENANT_NAME=default_tenant
CHROMA_DATABASE_NAME=default_database
CHROMA_COLLECTION_NAME=ai_video
CHROMA_INITIALIZE_SCHEMA=true

OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_EMBEDDING_MODEL=nomic-embed-text
SPRING_AI_MODEL_EMBEDDING=ollama
```

## 4. 启动后端并验证

```bash
cd backend
mvn spring-boot:run
```

访问：

```bash
curl http://localhost:8080/api/v1/system/vector/health
```

## 5. 常见问题

- `Connection refused`：确认 Chroma/Ollama 已启动，端口与 `.env` 一致。
- `model not found`：执行 `ollama pull nomic-embed-text` 拉取模型。
- `collection unavailable`：若不希望自动创建，设置 `CHROMA_INITIALIZE_SCHEMA=false` 并确保 tenant/database/collection 已预建。
