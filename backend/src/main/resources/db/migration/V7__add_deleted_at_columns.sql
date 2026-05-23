-- Flyway V7: 为核心业务表补充 deleted_at 逻辑删除时间
-- 兼容性说明：
-- 1) 部分 MySQL 版本不支持 `ADD COLUMN IF NOT EXISTS`
-- 2) 本脚本通过 information_schema 判定列是否存在后再执行 ALTER

DROP PROCEDURE IF EXISTS add_deleted_at_if_missing;

DELIMITER $$
CREATE PROCEDURE add_deleted_at_if_missing(IN p_table_name VARCHAR(128))
BEGIN
    DECLARE v_exists INT DEFAULT 0;

    SELECT COUNT(*)
    INTO v_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = 'deleted_at';

    IF v_exists = 0 THEN
        SET @ddl_sql = CONCAT(
                'ALTER TABLE `',
                p_table_name,
                '` ADD COLUMN `deleted_at` DATETIME(3) NULL COMMENT ''逻辑删除时间'''
                       );
        PREPARE ddl_stmt FROM @ddl_sql;
        EXECUTE ddl_stmt;
        DEALLOCATE PREPARE ddl_stmt;
    END IF;
END $$
DELIMITER ;

CALL add_deleted_at_if_missing('analysis_video_material');
CALL add_deleted_at_if_missing('creative_material');
CALL add_deleted_at_if_missing('video_analysis_task');
CALL add_deleted_at_if_missing('shot');
CALL add_deleted_at_if_missing('asr_segment');
CALL add_deleted_at_if_missing('key_frame');
CALL add_deleted_at_if_missing('analysis_result_core');
CALL add_deleted_at_if_missing('analysis_result_text_asset');
CALL add_deleted_at_if_missing('analysis_result_timeline_asset');
CALL add_deleted_at_if_missing('deconstruct_project');
CALL add_deleted_at_if_missing('deconstruct_project_material');

DROP PROCEDURE IF EXISTS add_deleted_at_if_missing;

