-- V27: 渲染版本化 — 记录每次渲染历史，保留历史版本文件不覆盖
-- 1) creation_project 增加 latest_render_id 指向最近一次渲染
-- 2) 新建 render_record 表记录每次渲染的版本信息

ALTER TABLE creation_project
    ADD COLUMN latest_render_id VARCHAR(128) NULL COMMENT '最近一次渲染 ID (projectId_timestamp)';

CREATE TABLE IF NOT EXISTS render_record (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    render_id     VARCHAR(128) NOT NULL COMMENT '渲染唯一标识 (projectId_timestamp)',
    project_id    VARCHAR(64)  NOT NULL COMMENT '所属项目 ID',
    status        VARCHAR(32)  NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED / RENDERING / DONE / FAILED',
    output_path   VARCHAR(512) NULL     COMMENT 'Remotion 输出文件路径',
    aspect_ratio  VARCHAR(16)  NULL     COMMENT '目标宽高比 (如 16:9)',
    created_at    DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_render_id (render_id),
    INDEX         idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渲染版本记录';
