-- Flyway V16: 创建品类知识库表并统一时序资产大 JSON

-- 1. 创建品类知识库字典表
CREATE TABLE IF NOT EXISTS category_knowledge (
    category_id         VARCHAR(100) PRIMARY KEY COMMENT '品类标识符',
    category_name       VARCHAR(100) NOT NULL COMMENT '品类中文名',
    dynamic_fields      JSON NULL COMMENT '动态扩展字段矩阵 (dynamicExtensionFields)',
    prompt_overrides    JSON NULL COMMENT '该品类专属的分析策略',
    scene_threshold     DOUBLE NOT NULL DEFAULT 0.25 COMMENT '该品类的最佳镜头切分阈值',
    discovered_by_llm   TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为大模型自动发现',
    confidence_score    DOUBLE NOT NULL DEFAULT 1.0 COMMENT '品类置信度(0~1)',
    usage_count         INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '被使用/分析命中的次数',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='品类结构知识库与最佳阈值表';

-- 2. 插入初始化种子数据
INSERT IGNORE INTO category_knowledge (category_id, category_name, scene_threshold, discovered_by_llm, confidence_score, usage_count, dynamic_fields)
VALUES 
('editing', '剪辑类', 0.4, 0, 1.0, 0, '[{"fieldName": "cutFrequency", "fieldType": "STRING", "fieldValue": "high", "description": "剪辑切换频率"}, {"fieldName": "motionEffects", "fieldType": "ARRAY", "fieldValue": ["speed_ramp","zoom_punch","shake"], "description": "常用运动特效列表"}, {"fieldName": "colorGrading", "fieldType": "STRING", "fieldValue": "cinematic_teal_orange", "description": "调色风格"}, {"fieldName": "syncStrategy", "fieldType": "STRING", "fieldValue": "beat_match", "description": "音画同步策略"}, {"fieldName": "bpmRange", "fieldType": "MAP", "fieldValue": {"min":110,"max":140}, "description": "适配的BGM BPM范围"}]'),

('marketing', '营销类', 0.3, 0, 1.0, 0, '[{"fieldName": "hookType", "fieldType": "STRING", "fieldValue": "question", "description": "钩子类型"}, {"fieldName": "hookOptions", "fieldType": "ARRAY", "fieldValue": ["question","pain_point","data_shock","controversy"], "description": "可选钩子策略池"}, {"fieldName": "sellingPointCount", "fieldType": "NUMBER", "fieldValue": 3, "description": "推荐卖点数量"}, {"fieldName": "ctaType", "fieldType": "STRING", "fieldValue": "click_link", "description": "行动号召类型"}, {"fieldName": "socialProofType", "fieldType": "STRING", "fieldValue": "review_count", "description": "社会认同方式"}]'),

('motion_graphics', 'MG/动态海报类', 0.25, 0, 1.0, 0, '[{"fieldName": "animationStyle", "fieldType": "STRING", "fieldValue": "flat_2d", "description": "动画风格"}, {"fieldName": "textAnimations", "fieldType": "ARRAY", "fieldValue": ["typewriter","slide_in","scale_pop"], "description": "文字动效列表"}, {"fieldName": "keyframeCount", "fieldType": "NUMBER", "fieldValue": 5, "description": "关键帧数量"}, {"fieldName": "loopable", "fieldType": "BOOLEAN", "fieldValue": true, "description": "是否可循环播放"}, {"fieldName": "colorScheme", "fieldType": "MAP", "fieldValue": {"primary":"#FF6B35","secondary":"#004E89","accent":"#FFC857"}, "description": "配色方案"}]');

-- 3. 改造 analysis_result_timeline_asset
-- 废弃三分叉，改为大一统的 video_structure_template_json
-- 将原 timeline_log_json 改为 fat_timeline_json (高敏胖数据)，新增 refined_timeline_json (提纯瘦数据)
ALTER TABLE analysis_result_timeline_asset
    DROP COLUMN shot_summary_json,
    DROP COLUMN script_structure_json,
    DROP COLUMN rhythm_structure_json,
    DROP COLUMN packaging_structure_json,
    CHANGE COLUMN timeline_log_json fat_timeline_json JSON NULL COMMENT '高敏多模态时序日志(胖JSON)',
    ADD COLUMN refined_timeline_json JSON NULL COMMENT '最佳阈值提纯后的时序日志(瘦JSON)' AFTER fat_timeline_json,
    ADD COLUMN video_structure_template_json JSON NULL COMMENT 'LLM统一解析输出的结构模板(含三层与动态扩展)' AFTER refined_timeline_json;
