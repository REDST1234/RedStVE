-- Flyway V6: 新增拆解项目与素材关联表（多对多）
-- 说明：
-- 1) 使用 project_id 关联拆解项目、material_biz_id 关联拆解素材
-- 2) 不设计物理外键，关系由应用层维护

CREATE TABLE IF NOT EXISTS deconstruct_project_material (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id          BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    project_id      VARCHAR(64) NOT NULL COMMENT '拆解项目ID(deconstruct_project.project_id)',
    material_biz_id BIGINT UNSIGNED NOT NULL COMMENT '素材业务主键(analysis_video_material.biz_id)',
    task_id         VARCHAR(36) NULL COMMENT '上传触发的任务ID(video_analysis_task.task_id)',
    relation_type   VARCHAR(20) NOT NULL DEFAULT 'PRIMARY' COMMENT '关联类型: PRIMARY/REFERENCE',
    sort_order      INT NOT NULL DEFAULT 0 COMMENT '项目内素材排序(越小越靠前)',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    CONSTRAINT uk_deconstruct_project_material_biz_id UNIQUE (biz_id),
    CONSTRAINT uk_project_material UNIQUE (project_id, material_biz_id),
    CONSTRAINT chk_relation_type CHECK (relation_type IN ('PRIMARY','REFERENCE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拆解项目与素材关联表';

CREATE INDEX idx_project_id ON deconstruct_project_material (project_id);
CREATE INDEX idx_material_biz_id ON deconstruct_project_material (material_biz_id);
CREATE INDEX idx_task_id ON deconstruct_project_material (task_id);
