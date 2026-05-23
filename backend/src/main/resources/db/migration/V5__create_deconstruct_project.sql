-- Flyway V5: 新增拆解项目模型与索引
-- 说明：
-- 1) project_id 为对外项目标识（prj_xxx）
-- 2) biz_id 为业务主键（雪花ID，由 MyBatis-Plus 自动填充）
-- 3) 不设计物理外键，关系由应用层维护

CREATE TABLE IF NOT EXISTS deconstruct_project (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id       BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    project_id   VARCHAR(64) NOT NULL COMMENT '对外项目ID(prj_xxx)',
    title        VARCHAR(200) NOT NULL COMMENT '项目标题',
    description  VARCHAR(1000) NULL COMMENT '项目描述',
    tags_json    JSON NULL COMMENT '项目标签(JSON数组)',
    cover_url    VARCHAR(1024) NULL COMMENT '封面URL(支持http(s)/file/相对路径/绝对路径)',
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '项目状态: PENDING/PROCESSING/COMPLETED/FAILED',
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    CONSTRAINT uk_deconstruct_project_biz_id UNIQUE (biz_id),
    CONSTRAINT uk_deconstruct_project_project_id UNIQUE (project_id),
    CONSTRAINT chk_deconstruct_project_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拆解项目主表';

CREATE INDEX idx_deconstruct_project_status_updated ON deconstruct_project (status, updated_at);
CREATE INDEX idx_deconstruct_project_title ON deconstruct_project (title);
