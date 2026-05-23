-- Flyway V10: 为缺少 updated_at 的分析结果子表补充 updated_at 列
-- 说明：
-- 1) asr_segment, key_frame, shot 表在 V1 创建时未包含 updated_at，但实体继承了 BaseEntity。
-- 2) 补充该列以解决 MyBatis Plus 自动填充时的 Unknown column 'updated_at' 异常。

DROP PROCEDURE IF EXISTS add_updated_at_if_missing;

DELIMITER $$
CREATE PROCEDURE add_updated_at_if_missing(IN p_table_name VARCHAR(128))
BEGIN
    DECLARE v_exists INT DEFAULT 0;

    SELECT COUNT(*)
    INTO v_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = 'updated_at';

    IF v_exists = 0 THEN
        SET @ddl_sql = CONCAT(
                'ALTER TABLE `',
                p_table_name,
                '` ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT ''更新时间'''
                       );
        PREPARE ddl_stmt FROM @ddl_sql;
        EXECUTE ddl_stmt;
        DEALLOCATE PREPARE ddl_stmt;
    END IF;
END $$
DELIMITER ;

CALL add_updated_at_if_missing('asr_segment');
CALL add_updated_at_if_missing('key_frame');
CALL add_updated_at_if_missing('shot');

DROP PROCEDURE IF EXISTS add_updated_at_if_missing;
