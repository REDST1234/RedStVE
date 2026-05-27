# Flyway 迁移指南

## 1. 已接入内容
- 依赖：`org.flywaydb:flyway-core`
- 迁移目录：`backend/src/main/resources/db/migration`
- 首个版本脚本：`V1__p0_video_analysis_schema.sql`
- 历史表：`flyway_schema_history`

## 2. 启动时自动迁移
项目启动时会自动执行 Flyway（默认开启）。

关键配置（`backend/src/main/resources/application.yml`）：
- `spring.flyway.enabled=true`
- `spring.flyway.locations=classpath:db/migration`
- `spring.flyway.baseline-on-migrate=true`
- `spring.flyway.validate-on-migrate=true`
- `spring.flyway.clean-disabled=true`

说明：
- `baseline-on-migrate=true` 适合已有历史库接入 Flyway：若库里已有表但没有 `flyway_schema_history`，会先 baseline，再从后续版本接管。

## 3. 新增迁移脚本规范
命名格式：
- `V{版本号}__{描述}.sql`

示例：
- `V2__add_video_analysis_indexes.sql`
- `V3__add_template_tables.sql`

约定：
- 版本号只增不改。
- 已上线脚本禁止修改；如需修复，新增下一个版本脚本。
- 每个 SQL 字段都写 `COMMENT`（遵循当前项目规范）。

## 4. 本地使用步骤
1. 确认数据库连接（`application-dev.yml` 中 `spring.datasource.*`）。
2. 启动后端：Flyway 会自动执行未应用版本。
3. 查看 `flyway_schema_history`，确认版本状态为 `Success`。

## 5. 常见场景
### 场景A：空库初始化
- 启动应用后自动执行 `V1`，完成建表。

### 场景B：已有旧库接入
- 保持 `baseline-on-migrate=true`。
- 首次启动会创建 `flyway_schema_history` 并 baseline，然后由后续版本接管。

### 场景C：新增表/字段
- 新建 `V{N}__*.sql`，提交代码。
- 启动应用自动迁移到最新版本。

## 6. 故障排查
### 校验失败（Validate failed）
- 原因：历史脚本被改动或库结构与脚本不一致。
- 处理：不要改旧脚本，新增新版本脚本修复；必要时人工比对 `flyway_schema_history`。

### 迁移中断
- 查看应用日志中的 Flyway 报错 SQL。
- 修复后重新启动，Flyway 会从失败版本继续。

### 禁止清库
- 项目已配置 `clean-disabled=true`，防止误执行 destructive clean。
