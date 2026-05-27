-- Flyway V14: 支持 key_frame 逻辑删除后重插
-- 目标：
-- 1) 保留历史逻辑删除关键帧
-- 2) 仅约束同一 task 的“未删除帧序号”唯一

DROP PROCEDURE IF EXISTS rebuild_key_frame_active_unique;

DELIMITER $$
CREATE PROCEDURE rebuild_key_frame_active_unique()
BEGIN
    DECLARE v_idx_exists INT DEFAULT 0;
    DECLARE v_col_exists INT DEFAULT 0;

    SELECT COUNT(*)
    INTO v_col_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'key_frame'
      AND COLUMN_NAME = 'active_frame_index';

    IF v_col_exists = 0 THEN
        ALTER TABLE key_frame
            ADD COLUMN active_frame_index TINYINT UNSIGNED
                GENERATED ALWAYS AS (
                    CASE
                        WHEN deleted_at IS NULL THEN frame_index
                        ELSE NULL
                        END
                    ) STORED COMMENT '活跃帧序号(仅未删除记录有值)';
    END IF;

    SELECT COUNT(*)
    INTO v_idx_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'key_frame'
      AND INDEX_NAME = 'uk_task_frame';

    IF v_idx_exists > 0 THEN
        ALTER TABLE key_frame DROP INDEX uk_task_frame;
    END IF;

    SELECT COUNT(*)
    INTO v_idx_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'key_frame'
      AND INDEX_NAME = 'uk_task_active_frame';

    IF v_idx_exists = 0 THEN
        ALTER TABLE key_frame
            ADD UNIQUE INDEX uk_task_active_frame (task_id, active_frame_index);
    END IF;

    SELECT COUNT(*)
    INTO v_idx_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'key_frame'
      AND INDEX_NAME = 'idx_keyframe_task_frame_deleted';

    IF v_idx_exists = 0 THEN
        ALTER TABLE key_frame
            ADD INDEX idx_keyframe_task_frame_deleted (task_id, frame_index, deleted_at);
    END IF;
END $$
DELIMITER ;

CALL rebuild_key_frame_active_unique();

DROP PROCEDURE IF EXISTS rebuild_key_frame_active_unique;
