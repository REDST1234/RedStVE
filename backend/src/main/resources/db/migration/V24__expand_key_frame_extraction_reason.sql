-- Active: 1771828032394@@100.79.235.109@3306@redstve
-- Flyway V24: 扩展 key_frame.extraction_reason 的 CHECK 约束
-- 目标：
-- 1) 兼容 P0 关键帧策略新增的抽帧原因
-- 2) 保持旧值 HOOK_FIRST / HOOK_MID / TOP_SCORE 继续可用

ALTER TABLE key_frame DROP CHECK chk_extraction_reason;

ALTER TABLE key_frame
ADD CONSTRAINT chk_extraction_reason CHECK (
    extraction_reason IN (
        'HOOK_FIRST',
        'HOOK_MID',
        'TOP_SCORE',
        'BOUNDARY_PRE',
        'BOUNDARY_POST',
        'UNIFORM_SAMPLE',
        'LONG_SHOT_MID'
    )
);