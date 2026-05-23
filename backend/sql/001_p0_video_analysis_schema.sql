-- P0: 视频拆解 + 创作素材核心模型
-- 目标：
-- 1) 拆解流素材库与创作流素材库物理隔离
-- 2) 各表统一维护雪花算法业务主键 biz_id

CREATE TABLE IF NOT EXISTS analysis_video_material (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id           BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    file_path        VARCHAR(500) NOT NULL COMMENT '视频文件路径',
    file_size        BIGINT UNSIGNED NULL COMMENT '文件大小(字节)',
    duration         DECIMAL(10,3) NULL COMMENT '视频时长(秒)',
    width            SMALLINT UNSIGNED NULL COMMENT '视频宽度',
    height           SMALLINT UNSIGNED NULL COMMENT '视频高度',
    format           VARCHAR(20) NULL COMMENT '视频格式(mp4/mov/avi/webm)',
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/PROCESSING/DELETED',
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    CONSTRAINT uk_analysis_video_material_biz_id UNIQUE (biz_id),
    CONSTRAINT chk_analysis_video_material_status CHECK (status IN ('ACTIVE','PROCESSING','DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拆解流视频素材库(仅视频)';

CREATE INDEX idx_analysis_video_material_status_created ON analysis_video_material (status, created_at);

CREATE TABLE IF NOT EXISTS creative_material (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id           BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    material_type    VARCHAR(20) NOT NULL COMMENT '素材类型: TEXT/IMAGE/VIDEO',
    file_path        VARCHAR(500) NULL COMMENT '素材文件路径(文本可为空)',
    text_content     TEXT NULL COMMENT '文案内容(material_type=TEXT时使用)',
    file_size        BIGINT UNSIGNED NULL COMMENT '文件大小(字节)',
    duration         DECIMAL(10,3) NULL COMMENT '时长(视频)',
    width            SMALLINT UNSIGNED NULL COMMENT '宽度(图片/视频)',
    height           SMALLINT UNSIGNED NULL COMMENT '高度(图片/视频)',
    format           VARCHAR(20) NULL COMMENT '格式(jpg/png/webp/mp4等)',
    tags             JSON NULL COMMENT '标签(JSON数组)',
    description      VARCHAR(500) NULL COMMENT '素材描述',
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/PROCESSING/DELETED',
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    CONSTRAINT uk_creative_material_biz_id UNIQUE (biz_id),
    CONSTRAINT chk_creative_material_type CHECK (material_type IN ('TEXT','IMAGE','VIDEO')),
    CONSTRAINT chk_creative_material_status CHECK (status IN ('ACTIVE','PROCESSING','DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='创作流素材库(文案/图片/视频)';

CREATE INDEX idx_creative_material_type_status_created ON creative_material (material_type, status, created_at);

CREATE TABLE IF NOT EXISTS video_analysis_task (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id             BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    task_id            VARCHAR(36) NOT NULL COMMENT '任务ID(兼容现有API，UUID)',
    source_video_biz_id BIGINT UNSIGNED NOT NULL COMMENT '来源视频业务主键(analysis_video_material.biz_id)',
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态: PENDING/PROCESSING/ANALYZING/COMPLETED/FAILED/CANCELED',
    progress           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '任务进度百分比(0-100)',
    progress_step      VARCHAR(50) NULL COMMENT '当前处理步骤描述',
    source_file_path   VARCHAR(500) NOT NULL COMMENT '源视频路径快照(便于审计)',
    file_size          BIGINT UNSIGNED NULL COMMENT '文件大小(字节)',
    error_message      TEXT NULL COMMENT '失败错误信息',
    error_step         VARCHAR(50) NULL COMMENT '失败步骤',
    retry_count        TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试次数(最大3)',
    priority           TINYINT UNSIGNED NOT NULL DEFAULT 5 COMMENT '任务优先级(1最高,10最低)',
    redis_progress_key VARCHAR(100) NULL COMMENT 'Redis进度缓存Key',
    created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    completed_at       DATETIME(3) NULL COMMENT '完成时间',

    CONSTRAINT uk_video_analysis_task_biz_id UNIQUE (biz_id),
    CONSTRAINT uk_video_analysis_task_task_id UNIQUE (task_id),
    CONSTRAINT chk_task_progress CHECK (progress >= 0 AND progress <= 100),
    CONSTRAINT chk_task_status CHECK (status IN ('PENDING','PROCESSING','ANALYZING','COMPLETED','FAILED','CANCELED')),
    CONSTRAINT chk_task_priority CHECK (priority >= 1 AND priority <= 10),
    CONSTRAINT chk_task_retry CHECK (retry_count <= 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频拆解任务主表';

CREATE INDEX idx_task_status_priority ON video_analysis_task (status, priority);
CREATE INDEX idx_task_created_at ON video_analysis_task (created_at);
CREATE INDEX idx_task_status_updated ON video_analysis_task (status, updated_at);
CREATE INDEX idx_task_source_video_biz_id ON video_analysis_task (source_video_biz_id);

CREATE TABLE IF NOT EXISTS shot (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id         BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    task_id        VARCHAR(36) NOT NULL COMMENT '关联任务ID(video_analysis_task.task_id)',
    shot_index     SMALLINT UNSIGNED NOT NULL COMMENT '镜头序号(从0开始)',
    start_time     DECIMAL(10,3) NOT NULL COMMENT '镜头起始时间(秒)',
    end_time       DECIMAL(10,3) NOT NULL COMMENT '镜头结束时间(秒)',
    duration       DECIMAL(10,3) NOT NULL COMMENT '镜头时长(秒)',
    scene_score    DECIMAL(4,3) NULL COMMENT '场景变化分值(0-1)',
    thumbnail_path VARCHAR(500) NULL COMMENT '镜头缩略图路径',
    shot_type      VARCHAR(30) NULL COMMENT '镜头类型(LLM推断)',
    description    VARCHAR(500) NULL COMMENT '镜头描述(LLM生成)',
    created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

    CONSTRAINT uk_shot_biz_id UNIQUE (biz_id),
    CONSTRAINT uk_task_shot UNIQUE (task_id, shot_index),
    CONSTRAINT chk_shot_time CHECK (end_time > start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='镜头切分结果';

CREATE INDEX idx_shot_task ON shot (task_id);
CREATE INDEX idx_shot_score ON shot (task_id, scene_score DESC);

CREATE TABLE IF NOT EXISTS asr_segment (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id        BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    task_id       VARCHAR(36) NOT NULL COMMENT '关联任务ID(video_analysis_task.task_id)',
    segment_index SMALLINT UNSIGNED NOT NULL COMMENT 'ASR片段序号',
    text          TEXT NOT NULL COMMENT 'ASR转写文本',
    start_time    DECIMAL(10,3) NOT NULL COMMENT '片段起始时间(秒)',
    end_time      DECIMAL(10,3) NOT NULL COMMENT '片段结束时间(秒)',
    speaker_label VARCHAR(20) NULL COMMENT '说话人标签',
    confidence    DECIMAL(4,3) NULL COMMENT 'ASR置信度',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

    CONSTRAINT uk_asr_segment_biz_id UNIQUE (biz_id),
    CONSTRAINT uk_task_asr_seg UNIQUE (task_id, segment_index),
    CONSTRAINT chk_asr_time CHECK (end_time > start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ASR转写片段';

CREATE INDEX idx_asr_task ON asr_segment (task_id);
CREATE INDEX idx_asr_time ON asr_segment (task_id, start_time);

CREATE TABLE IF NOT EXISTS key_frame (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id            BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    task_id           VARCHAR(36) NOT NULL COMMENT '关联任务ID(video_analysis_task.task_id)',
    frame_index       TINYINT UNSIGNED NOT NULL COMMENT '关键帧序号(1-5)',
    time_point        DECIMAL(10,3) NOT NULL COMMENT '抽帧时间点(秒)',
    source_shot_index SMALLINT UNSIGNED NULL COMMENT '来源镜头序号',
    extraction_reason VARCHAR(20) NOT NULL COMMENT '抽帧原因(HOOK_FIRST/HOOK_MID/TOP_SCORE)',
    file_path         VARCHAR(500) NOT NULL COMMENT '关键帧文件路径',
    description       TEXT NULL COMMENT '关键帧文本描述(用于Prompt)',
    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

    CONSTRAINT uk_key_frame_biz_id UNIQUE (biz_id),
    CONSTRAINT uk_task_frame UNIQUE (task_id, frame_index),
    CONSTRAINT chk_frame_index CHECK (frame_index >= 1 AND frame_index <= 5),
    CONSTRAINT chk_extraction_reason CHECK (extraction_reason IN ('HOOK_FIRST','HOOK_MID','TOP_SCORE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关键帧抽取结果';

CREATE INDEX idx_keyframe_task ON key_frame (task_id);

CREATE TABLE IF NOT EXISTS analysis_result (
    id                        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id                    BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    task_id                   VARCHAR(36) NOT NULL COMMENT '关联任务ID(一对一)',
    video_info                JSON NOT NULL COMMENT '视频基础信息(JSON)',
    shot_summary              JSON NULL COMMENT '镜头摘要(JSON)',
    transcript_summary        JSON NULL COMMENT '转写摘要(JSON)',
    timeline_log              JSON NULL COMMENT '多模态时间线日志(JSON)',
    script_structure          JSON NULL COMMENT '脚本结构(JSON)',
    rhythm_structure          JSON NULL COMMENT '节奏结构(JSON)',
    packaging_structure       JSON NULL COMMENT '包装结构(JSON)',
    partial_failed_dimensions JSON NULL COMMENT '局部失败维度(JSON数组)',
    category_id               VARCHAR(100) NULL COMMENT 'LLM判定品类ID',
    llm_model_used            VARCHAR(50) NULL COMMENT '使用的LLM模型标识',
    llm_token_usage           JSON NULL COMMENT 'Token消耗统计(JSON)',
    schema_version            VARCHAR(10) NOT NULL DEFAULT 'v2' COMMENT '结构Schema版本',
    created_at                DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

    CONSTRAINT uk_analysis_result_biz_id UNIQUE (biz_id),
    CONSTRAINT uk_task_analysis UNIQUE (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频拆解分析聚合结果';

CREATE INDEX idx_analysis_category ON analysis_result (category_id);
