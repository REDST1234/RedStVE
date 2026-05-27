CREATE TABLE `creation_project` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `project_id` VARCHAR(64) NOT NULL COMMENT '业务唯一 ID',
  `title` VARCHAR(255) COMMENT '项目标题',
  `description` TEXT COMMENT '项目描述',
  `template_id` VARCHAR(64) COMMENT '绑定的结构模板 ID',
  `template_snapshot_json` LONGTEXT COMMENT '绑定时的模板快照',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/MATCHING/ADAPTING/COMPOSED/EXPORTED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted_at` DATETIME(3),
  UNIQUE KEY `uk_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='创作项目表';

ALTER TABLE `creative_material`
  ADD COLUMN `project_id` VARCHAR(64) NULL COMMENT '所属创作项目ID' AFTER `biz_id`,
  ADD COLUMN `profile_json` LONGTEXT NULL COMMENT '三层资产档案 JSON' AFTER `status`;

-- 移除旧的 check 约束以支持新的状态字典
ALTER TABLE `creative_material` DROP CHECK `chk_creative_material_status`;

CREATE INDEX idx_creative_material_project_id ON `creative_material` (`project_id`);

CREATE TABLE `slot_match_result` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `match_id` VARCHAR(64) NOT NULL COMMENT '业务唯一 ID',
  `project_id` VARCHAR(64) NOT NULL COMMENT '所属创作项目（无物理外键）',
  `segment_index` INT NOT NULL COMMENT '模板段落索引',
  `segment_role` VARCHAR(32) COMMENT 'hook/body/climax/outro',
  `matched_asset_id` VARCHAR(64) COMMENT '匹配上的素材 ID',
  `match_score` DOUBLE COMMENT '匹配度',
  `match_status` VARCHAR(32) NOT NULL COMMENT 'MATCHED/PARTIAL/MISSING',
  `version_id` VARCHAR(64) NOT NULL COMMENT '编排版本幂等控制标识',
  `adaptation_plan_json` LONGTEXT COMMENT '适配方案 JSON',
  `adapted_file_path` VARCHAR(512) COMMENT '适配产物路径',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted_at` DATETIME(3),
  UNIQUE KEY `uk_match_id` (`match_id`),
  KEY `idx_project_id_version` (`project_id`, `version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='槽位匹配结果表';
