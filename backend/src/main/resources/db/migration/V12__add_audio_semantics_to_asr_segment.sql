ALTER TABLE asr_segment
    ADD COLUMN audio_emotion VARCHAR(20) NULL COMMENT '音频情绪标签' AFTER confidence,
    ADD COLUMN volume_intensity VARCHAR(20) NULL COMMENT '音量强度标签' AFTER audio_emotion,
    ADD COLUMN background_environment VARCHAR(20) NULL COMMENT '背景音环境标签' AFTER volume_intensity;
