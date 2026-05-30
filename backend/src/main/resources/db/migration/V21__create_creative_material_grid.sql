CREATE TABLE IF NOT EXISTS creative_material_grid (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id          BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    material_biz_id BIGINT UNSIGNED NOT NULL COMMENT '素材业务主键',
    page_index      INT NOT NULL COMMENT '宫格分页索引(从1开始)',
    file_path       VARCHAR(1024) NOT NULL COMMENT '宫格文件路径',
    frame_count     INT NOT NULL COMMENT '当前页帧槽位数',
    fps             INT NOT NULL COMMENT '抽帧fps',
    grid_cols       INT NOT NULL COMMENT '宫格列数',
    grid_rows       INT NOT NULL COMMENT '宫格行数',
    status          VARCHAR(32) NOT NULL DEFAULT 'READY' COMMENT 'READY/FAILED',
    llm_included    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已参与LLM分析',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3) NULL COMMENT '逻辑删除时间',

    CONSTRAINT uk_creative_material_grid_biz_id UNIQUE (biz_id),
    CONSTRAINT uk_creative_material_grid_page UNIQUE (material_biz_id, page_index, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='创作素材宫格分页表';

CREATE INDEX idx_creative_material_grid_material ON creative_material_grid (material_biz_id);
CREATE INDEX idx_creative_material_grid_status ON creative_material_grid (status);
