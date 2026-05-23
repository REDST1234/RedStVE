-- Flyway V8: 新增 original_file_name 字段
-- 目的：解耦物理文件名和业务文件名，防止长中文名导致文件系统长度溢出。

ALTER TABLE analysis_video_material
    ADD COLUMN original_file_name VARCHAR(255) NULL COMMENT '原始文件名' AFTER biz_id;

ALTER TABLE creative_material
    ADD COLUMN original_file_name VARCHAR(255) NULL COMMENT '原始文件名' AFTER biz_id;
