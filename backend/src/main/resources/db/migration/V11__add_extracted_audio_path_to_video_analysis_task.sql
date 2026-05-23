ALTER TABLE video_analysis_task
    ADD COLUMN extracted_audio_path VARCHAR(1024) NULL COMMENT '提取后的音频文件路径(持久化保留)' AFTER source_file_path;
