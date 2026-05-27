-- Flyway V13: 支持 asr_segment 逻辑删除后重插
-- 目标：
-- 1) 保留历史逻辑删除数据
-- 2) 仍约束同一 task 的“未删除片段序号”唯一

DROP PROCEDURE IF EXISTS rebuild_asr_segment_active_unique;

DELIMITER $$
CREATE PROCEDURE rebuild_asr_segment_active_unique()
BEGIN
    DECLARE v_idx_exists INT DEFAULT 0;
    DECLARE v_col_exists INT DEFAULT 0;

    SELECT COUNT(*)
    INTO v_col_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'asr_segment'
      AND COLUMN_NAME = 'active_segment_index';

    IF v_col_exists = 0 THEN
        ALTER TABLE asr_segment
            ADD COLUMN active_segment_index SMALLINT UNSIGNED
                GENERATED ALWAYS AS (
                    CASE
                        WHEN deleted_at IS NULL THEN segment_index
                        ELSE NULL
                        END
                    ) STORED COMMENT '活跃片段序号(仅未删除记录有值)';
    END IF;

    SELECT COUNT(*)
    INTO v_idx_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'asr_segment'
      AND INDEX_NAME = 'uk_task_asr_seg';

    IF v_idx_exists > 0 THEN
        ALTER TABLE asr_segment DROP INDEX uk_task_asr_seg;
    END IF;

    SELECT COUNT(*)
    INTO v_idx_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'asr_segment'
      AND INDEX_NAME = 'uk_task_asr_active_seg';

    IF v_idx_exists = 0 THEN
        ALTER TABLE asr_segment
            ADD UNIQUE INDEX uk_task_asr_active_seg (task_id, active_segment_index);
    END IF;

    SELECT COUNT(*)
    INTO v_idx_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'asr_segment'
      AND INDEX_NAME = 'idx_asr_task_seg_deleted';

    IF v_idx_exists = 0 THEN
        ALTER TABLE asr_segment
            ADD INDEX idx_asr_task_seg_deleted (task_id, segment_index, deleted_at);
    END IF;
END $$
DELIMITER ;

CALL rebuild_asr_segment_active_unique();

DROP PROCEDURE IF EXISTS rebuild_asr_segment_active_unique;
