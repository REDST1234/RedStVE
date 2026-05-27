-- Flyway V15: 删除 key_frame 的数量上限约束
-- 目标：由于抽帧策略改为动态伸缩(最大15帧)，需要移除原本写死的 <= 5 帧的数据库 CHECK 约束

ALTER TABLE key_frame DROP CHECK chk_frame_index;
