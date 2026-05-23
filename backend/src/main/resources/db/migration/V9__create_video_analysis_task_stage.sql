-- Flyway V9: 新增任务阶段状态表，支持并行异步阶段独立推进
-- 设计目标：
-- 1) 替代 video_analysis_task.progress_step 的单点覆盖问题
-- 2) 支持 ASR/SCENE/KEYFRAME/OCR/LLM 等阶段并行更新
-- 3) 不使用物理外键，仅通过 task_id 逻辑关联

CREATE TABLE IF NOT EXISTS video_analysis_task_stage (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id        BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    task_id       VARCHAR(36) NOT NULL COMMENT '任务ID(逻辑关联video_analysis_task.task_id)',
    stage_type    VARCHAR(30) NOT NULL COMMENT '阶段类型: ASR/SCENE/KEYFRAME/OCR/LLM/TIMELINE',
    stage_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '阶段状态: PENDING/RUNNING/SUCCESS/FAILED/SKIPPED',
    stage_progress TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '阶段进度百分比(0-100)',
    error_message TEXT NULL COMMENT '阶段错误信息',
    started_at    DATETIME(3) NULL COMMENT '阶段开始时间',
    ended_at      DATETIME(3) NULL COMMENT '阶段结束时间',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at    DATETIME(3) NULL COMMENT '逻辑删除时间',

    CONSTRAINT uk_video_analysis_task_stage_task_type UNIQUE (task_id, stage_type),
    CONSTRAINT uk_video_analysis_task_stage_biz_id UNIQUE (biz_id),
    CONSTRAINT chk_video_analysis_task_stage_status CHECK (stage_status IN ('PENDING','RUNNING','SUCCESS','FAILED','SKIPPED')),
    CONSTRAINT chk_video_analysis_task_stage_progress CHECK (stage_progress >= 0 AND stage_progress <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频拆解任务阶段状态表';

CREATE INDEX idx_video_analysis_task_stage_task_id ON video_analysis_task_stage (task_id);
CREATE INDEX idx_video_analysis_task_stage_status ON video_analysis_task_stage (stage_status, updated_at);

