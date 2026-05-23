-- Flyway V2: 拆分 analysis_result 为三层结果表
-- 目标：
-- 1) 高频查询命中轻量 core 表
-- 2) 文本语义与时序大 JSON 分离，冷热隔离
-- 3) 仅保留逻辑关联，不设计物理外键约束

RENAME TABLE analysis_result TO analysis_result_legacy;

CREATE TABLE IF NOT EXISTS analysis_result_core (
    id                        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id                    BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    task_id                   VARCHAR(36) NOT NULL COMMENT '关联任务ID(一对一)',
    status                    VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT '结果状态: PENDING/PROCESSING/COMPLETED/FAILED/PARTIAL_SUCCESS',
    category_id               VARCHAR(100) NULL COMMENT 'LLM判定品类ID',
    llm_model_used            VARCHAR(50) NULL COMMENT '使用的LLM模型标识',
    schema_version            VARCHAR(10) NOT NULL DEFAULT 'v2' COMMENT '结构Schema版本',
    partial_failed_dimensions JSON NULL COMMENT '局部失败维度(JSON数组)',
    duration_sec              DECIMAL(10,3) NULL COMMENT '视频时长(秒)',
    width                     SMALLINT UNSIGNED NULL COMMENT '视频宽度',
    height                    SMALLINT UNSIGNED NULL COMMENT '视频高度',
    fps                       DECIMAL(10,3) NULL COMMENT '帧率',
    video_codec               VARCHAR(50) NULL COMMENT '视频编码',
    audio_codec               VARCHAR(50) NULL COMMENT '音频编码',
    has_audio                 TINYINT(1) NULL COMMENT '是否含音频(0/1)',
    bitrate                   BIGINT UNSIGNED NULL COMMENT '视频码率',
    shot_count                INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '镜头数量',
    asr_segment_count         INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'ASR片段数量',
    key_frame_count           INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '关键帧数量',
    created_at                DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at                DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    CONSTRAINT uk_analysis_result_core_biz_id UNIQUE (biz_id),
    CONSTRAINT uk_analysis_result_core_task_id UNIQUE (task_id),
    CONSTRAINT chk_analysis_result_core_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED','PARTIAL_SUCCESS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频拆解结果核心状态表(高频索引层)';

CREATE INDEX idx_core_status_created ON analysis_result_core (status, created_at);
CREATE INDEX idx_core_category ON analysis_result_core (category_id);
CREATE INDEX idx_core_task_status ON analysis_result_core (task_id, status);

CREATE TABLE IF NOT EXISTS analysis_result_text_asset (
    id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id                  BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    task_id                 VARCHAR(36) NOT NULL COMMENT '关联任务ID(一对一)',
    asr_full_text           LONGTEXT NULL COMMENT 'ASR全文(脱水文本)',
    ocr_full_text           LONGTEXT NULL COMMENT 'OCR全文(脱水文本)',
    transcript_summary_text LONGTEXT NULL COMMENT '转写摘要文本',
    keywords_text           TEXT NULL COMMENT '关键词文本(逗号分隔或自然语言)',
    created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    CONSTRAINT uk_analysis_result_text_biz_id UNIQUE (biz_id),
    CONSTRAINT uk_analysis_result_text_task_id UNIQUE (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频拆解文本语义资产表(全文检索层)';

CREATE FULLTEXT INDEX ft_text_semantic ON analysis_result_text_asset (asr_full_text, ocr_full_text, transcript_summary_text, keywords_text);

CREATE TABLE IF NOT EXISTS analysis_result_timeline_asset (
    id                     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    biz_id                 BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
    task_id                VARCHAR(36) NOT NULL COMMENT '关联任务ID(一对一)',
    shot_summary_json      JSON NULL COMMENT '镜头摘要JSON',
    timeline_log_json      JSON NULL COMMENT '多模态时序日志JSON',
    script_structure_json  JSON NULL COMMENT '脚本结构JSON',
    rhythm_structure_json  JSON NULL COMMENT '节奏结构JSON',
    packaging_structure_json JSON NULL COMMENT '包装结构JSON',
    llm_token_usage_json   JSON NULL COMMENT 'LLM Token消耗JSON',
    provenance_json        JSON NULL COMMENT '溯源节点JSON',
    created_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    CONSTRAINT uk_analysis_result_timeline_biz_id UNIQUE (biz_id),
    CONSTRAINT uk_analysis_result_timeline_task_id UNIQUE (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频拆解时序大资产表(冷数据层)';

CREATE INDEX idx_timeline_task ON analysis_result_timeline_asset (task_id);

INSERT INTO analysis_result_core (
    biz_id,
    task_id,
    status,
    category_id,
    llm_model_used,
    schema_version,
    partial_failed_dimensions,
    duration_sec,
    width,
    height,
    fps,
    video_codec,
    audio_codec,
    has_audio,
    bitrate,
    shot_count,
    asr_segment_count,
    key_frame_count,
    created_at,
    updated_at
)
SELECT
    ar.biz_id,
    ar.task_id,
    COALESCE(vat.status, 'COMPLETED') AS status,
    ar.category_id,
    ar.llm_model_used,
    ar.schema_version,
    ar.partial_failed_dimensions,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(ar.video_info, '$.duration')) AS DECIMAL(10,3)) AS duration_sec,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(ar.video_info, '$.width')) AS UNSIGNED) AS width,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(ar.video_info, '$.height')) AS UNSIGNED) AS height,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(ar.video_info, '$.fps')) AS DECIMAL(10,3)) AS fps,
    JSON_UNQUOTE(JSON_EXTRACT(ar.video_info, '$.codec')) AS video_codec,
    JSON_UNQUOTE(JSON_EXTRACT(ar.video_info, '$.audioCodec')) AS audio_codec,
    CASE JSON_UNQUOTE(JSON_EXTRACT(ar.video_info, '$.hasAudio'))
        WHEN 'true' THEN 1
        WHEN '1' THEN 1
        WHEN 'false' THEN 0
        WHEN '0' THEN 0
        ELSE NULL
    END AS has_audio,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(ar.video_info, '$.bitrate')) AS UNSIGNED) AS bitrate,
    COALESCE(sc.shot_count, 0) AS shot_count,
    COALESCE(ac.asr_segment_count, 0) AS asr_segment_count,
    COALESCE(kc.key_frame_count, 0) AS key_frame_count,
    ar.created_at,
    CURRENT_TIMESTAMP(3) AS updated_at
FROM analysis_result_legacy ar
LEFT JOIN video_analysis_task vat ON vat.task_id = ar.task_id
LEFT JOIN (
    SELECT task_id, COUNT(*) AS shot_count
    FROM shot
    GROUP BY task_id
) sc ON sc.task_id = ar.task_id
LEFT JOIN (
    SELECT task_id, COUNT(*) AS asr_segment_count
    FROM asr_segment
    GROUP BY task_id
) ac ON ac.task_id = ar.task_id
LEFT JOIN (
    SELECT task_id, COUNT(*) AS key_frame_count
    FROM key_frame
    GROUP BY task_id
) kc ON kc.task_id = ar.task_id;

INSERT INTO analysis_result_text_asset (
    biz_id,
    task_id,
    asr_full_text,
    ocr_full_text,
    transcript_summary_text,
    keywords_text,
    created_at,
    updated_at
)
SELECT
    ar.biz_id,
    ar.task_id,
    JSON_UNQUOTE(JSON_EXTRACT(ar.transcript_summary, '$.asrFullText')) AS asr_full_text,
    JSON_UNQUOTE(JSON_EXTRACT(ar.transcript_summary, '$.ocrFullText')) AS ocr_full_text,
    JSON_UNQUOTE(JSON_EXTRACT(ar.transcript_summary, '$.summary')) AS transcript_summary_text,
    JSON_UNQUOTE(JSON_EXTRACT(ar.transcript_summary, '$.keywords')) AS keywords_text,
    ar.created_at,
    CURRENT_TIMESTAMP(3) AS updated_at
FROM analysis_result_legacy ar;

INSERT INTO analysis_result_timeline_asset (
    biz_id,
    task_id,
    shot_summary_json,
    timeline_log_json,
    script_structure_json,
    rhythm_structure_json,
    packaging_structure_json,
    llm_token_usage_json,
    provenance_json,
    created_at,
    updated_at
)
SELECT
    ar.biz_id,
    ar.task_id,
    ar.shot_summary,
    ar.timeline_log,
    ar.script_structure,
    ar.rhythm_structure,
    ar.packaging_structure,
    ar.llm_token_usage,
    NULL AS provenance_json,
    ar.created_at,
    CURRENT_TIMESTAMP(3) AS updated_at
FROM analysis_result_legacy ar;
