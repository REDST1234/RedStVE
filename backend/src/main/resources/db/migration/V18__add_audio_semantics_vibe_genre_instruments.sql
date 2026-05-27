ALTER TABLE asr_segment
    ADD COLUMN vocal_vibe VARCHAR(255) NULL COMMENT '语音基调描述' AFTER background_environment,
    ADD COLUMN bgm_genre VARCHAR(100) NULL COMMENT '背景音乐流派' AFTER vocal_vibe,
    ADD COLUMN bgm_instruments VARCHAR(255) NULL COMMENT '背景音乐主导乐器组合' AFTER bgm_genre;
