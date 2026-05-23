-- Flyway V3: 清理拆分后的旧聚合表
-- 说明：请仅在 V2 上线稳定后执行该迁移。

DROP TABLE IF EXISTS analysis_result_legacy;
