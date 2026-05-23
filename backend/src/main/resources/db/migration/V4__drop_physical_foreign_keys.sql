-- Flyway V4: 全量移除物理外键约束
-- 说明：
-- 1) 兼容历史环境：若外键存在则删除，不存在则跳过
-- 2) 保留唯一键与业务索引，通过应用层维护逻辑关联完整性

SET @schema_name = DATABASE();

-- video_analysis_task(source_video_biz_id) -> analysis_video_material(biz_id)
SET @fk_name = 'fk_video_analysis_task_source_video';
SET @ddl = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND CONSTRAINT_NAME = @fk_name
        ),
        'ALTER TABLE video_analysis_task DROP FOREIGN KEY fk_video_analysis_task_source_video',
        'SELECT ''skip fk_video_analysis_task_source_video'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- shot(task_id) -> video_analysis_task(task_id)
SET @fk_name = 'fk_shot_task';
SET @ddl = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND CONSTRAINT_NAME = @fk_name
        ),
        'ALTER TABLE shot DROP FOREIGN KEY fk_shot_task',
        'SELECT ''skip fk_shot_task'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- asr_segment(task_id) -> video_analysis_task(task_id)
SET @fk_name = 'fk_asr_task';
SET @ddl = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND CONSTRAINT_NAME = @fk_name
        ),
        'ALTER TABLE asr_segment DROP FOREIGN KEY fk_asr_task',
        'SELECT ''skip fk_asr_task'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- key_frame(task_id) -> video_analysis_task(task_id)
SET @fk_name = 'fk_keyframe_task';
SET @ddl = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND CONSTRAINT_NAME = @fk_name
        ),
        'ALTER TABLE key_frame DROP FOREIGN KEY fk_keyframe_task',
        'SELECT ''skip fk_keyframe_task'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- analysis_result(task_id) -> video_analysis_task(task_id)
SET @fk_name = 'fk_analysis_task';
SET @ddl = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND CONSTRAINT_NAME = @fk_name
        ),
        'ALTER TABLE analysis_result_legacy DROP FOREIGN KEY fk_analysis_task',
        'SELECT ''skip fk_analysis_task'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- analysis_result_core(task_id) -> video_analysis_task(task_id)
SET @fk_name = 'fk_analysis_result_core_task';
SET @ddl = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND CONSTRAINT_NAME = @fk_name
        ),
        'ALTER TABLE analysis_result_core DROP FOREIGN KEY fk_analysis_result_core_task',
        'SELECT ''skip fk_analysis_result_core_task'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- analysis_result_text_asset(task_id) -> video_analysis_task(task_id)
SET @fk_name = 'fk_analysis_result_text_task';
SET @ddl = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND CONSTRAINT_NAME = @fk_name
        ),
        'ALTER TABLE analysis_result_text_asset DROP FOREIGN KEY fk_analysis_result_text_task',
        'SELECT ''skip fk_analysis_result_text_task'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- analysis_result_timeline_asset(task_id) -> video_analysis_task(task_id)
SET @fk_name = 'fk_analysis_result_timeline_task';
SET @ddl = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND CONSTRAINT_NAME = @fk_name
        ),
        'ALTER TABLE analysis_result_timeline_asset DROP FOREIGN KEY fk_analysis_result_timeline_task',
        'SELECT ''skip fk_analysis_result_timeline_task'''
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
