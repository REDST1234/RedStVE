-- Flyway V17: 拆解模板与创作项目解耦（全局模板库 + 项目快照）
-- 说明：
-- 1) 模板作为全局资产独立管理（deconstruct_template）
-- 2) 项目仅绑定不可变快照（project_template_snapshot），避免受模板后续变更影响
-- 3) 维持无物理外键策略，关系由应用层维护

CREATE TABLE IF NOT EXISTS deconstruct_template (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id           BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    template_id      VARCHAR(64) NOT NULL COMMENT '模板ID(tpl_xxx)',
    template_version INT UNSIGNED NOT NULL COMMENT '模板版本号(从1开始递增)',
    template_name    VARCHAR(200) NOT NULL COMMENT '模板名称',
    category_id      VARCHAR(100) NULL COMMENT '模板所属品类ID',
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '模板状态: DRAFT/PUBLISHED/ARCHIVED',
    source_task_id   VARCHAR(36) NULL COMMENT '模板来源拆解任务ID',
    template_json    JSON NOT NULL COMMENT '模板结构完整JSON',
    snapshot_hash    CHAR(64) NOT NULL COMMENT '模板JSON哈希(SHA-256)',
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at       DATETIME(3) NULL COMMENT '逻辑删除时间',

    CONSTRAINT uk_deconstruct_template_biz_id UNIQUE (biz_id),
    CONSTRAINT uk_deconstruct_template_id_ver UNIQUE (template_id, template_version),
    CONSTRAINT chk_deconstruct_template_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局拆解模板资产表';

CREATE INDEX idx_deconstruct_template_id_status ON deconstruct_template (template_id, status);
CREATE INDEX idx_deconstruct_template_category_status ON deconstruct_template (category_id, status);
CREATE INDEX idx_deconstruct_template_source_task ON deconstruct_template (source_task_id);

CREATE TABLE IF NOT EXISTS project_template_snapshot (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id           BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    snapshot_id      VARCHAR(64) NOT NULL COMMENT '快照ID(snap_xxx)',
    project_id       VARCHAR(64) NOT NULL COMMENT '项目ID(deconstruct_project.project_id)',
    template_id      VARCHAR(64) NOT NULL COMMENT '模板ID(deconstruct_template.template_id)',
    template_version INT UNSIGNED NOT NULL COMMENT '模板版本号',
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '快照状态: ACTIVE/REPLACED',
    source_task_id   VARCHAR(36) NULL COMMENT '模板来源拆解任务ID',
    snapshot_json    JSON NOT NULL COMMENT '项目绑定时的模板快照JSON',
    snapshot_hash    CHAR(64) NOT NULL COMMENT '快照JSON哈希(SHA-256)',
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at       DATETIME(3) NULL COMMENT '逻辑删除时间',

    CONSTRAINT uk_project_template_snapshot_biz_id UNIQUE (biz_id),
    CONSTRAINT uk_project_template_snapshot_id UNIQUE (snapshot_id),
    CONSTRAINT uk_project_template_snapshot_hash UNIQUE (project_id, template_id, template_version, snapshot_hash),
    CONSTRAINT chk_project_template_snapshot_status CHECK (status IN ('ACTIVE','REPLACED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目模板快照表(不可变绑定)';

CREATE INDEX idx_project_template_snapshot_project_status ON project_template_snapshot (project_id, status);
CREATE INDEX idx_project_template_snapshot_template_ver ON project_template_snapshot (template_id, template_version);
CREATE INDEX idx_project_template_snapshot_source_task ON project_template_snapshot (source_task_id);

CREATE TABLE IF NOT EXISTS scriptwriter_run_record (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id                BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    run_id                VARCHAR(64) NOT NULL COMMENT '编剧运行ID(run_xxx)',
    project_id            VARCHAR(64) NOT NULL COMMENT '项目ID(deconstruct_project.project_id)',
    snapshot_id           VARCHAR(64) NOT NULL COMMENT '模板快照ID(project_template_snapshot.snapshot_id)',
    material_biz_ids_json JSON NULL COMMENT '参与编排的素材业务主键列表(JSON数组)',
    final_prompt_text     LONGTEXT NOT NULL COMMENT '最终拼装提示词',
    status                VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT '运行状态: RUNNING/COMPLETED/FAILED',
    writer_output_json    JSON NULL COMMENT '编剧LLM输出JSON',
    error_message         TEXT NULL COMMENT '失败错误信息',
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at            DATETIME(3) NULL COMMENT '逻辑删除时间',

    CONSTRAINT uk_scriptwriter_run_record_biz_id UNIQUE (biz_id),
    CONSTRAINT uk_scriptwriter_run_record_run_id UNIQUE (run_id),
    CONSTRAINT chk_scriptwriter_run_status CHECK (status IN ('RUNNING','COMPLETED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='编剧LLM运行记录表';

CREATE INDEX idx_scriptwriter_run_project ON scriptwriter_run_record (project_id, created_at);
CREATE INDEX idx_scriptwriter_run_snapshot ON scriptwriter_run_record (snapshot_id);
