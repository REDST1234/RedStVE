ALTER TABLE `slot_match_result`
  ADD COLUMN `matched_highlight_id` VARCHAR(64) NULL COMMENT '匹配上的素材高光片段ID' AFTER `matched_asset_id`,
  ADD COLUMN `match_reason` TEXT NULL COMMENT 'LLM匹配原因' AFTER `match_status`,
  ADD COLUMN `veto_reason` TEXT NULL COMMENT 'LLM否决或缺口原因' AFTER `match_reason`;

CREATE TABLE IF NOT EXISTS `creation_ffmpeg_command_log` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
  `biz_id` BIGINT UNSIGNED NOT NULL COMMENT '业务主键(雪花ID)',
  `project_id` VARCHAR(64) NOT NULL COMMENT '创作项目ID',
  `version_id` VARCHAR(64) NOT NULL COMMENT '编排版本ID',
  `match_id` VARCHAR(64) NULL COMMENT '槽位匹配ID',
  `segment_index` INT NULL COMMENT '模板段落索引',
  `material_biz_id` VARCHAR(64) NULL COMMENT '素材业务ID',
  `strategy_type` VARCHAR(64) NULL COMMENT '策略类型',
  `command_text` LONGTEXT NULL COMMENT 'Java拼好的最终FFmpeg命令',
  `status` VARCHAR(32) NOT NULL COMMENT 'BUILT/RUNNING/SUCCESS/FAILED/VETOED',
  `veto_reason` TEXT NULL COMMENT '最终否决原因',
  `error_message` TEXT NULL COMMENT '最终错误原因',
  `exit_code` INT NULL COMMENT 'FFmpeg退出码',
  `elapsed_ms` BIGINT NULL COMMENT '执行耗时毫秒',
  `stdout_tail` TEXT NULL COMMENT '标准输出尾部摘要',
  `stderr_tail` TEXT NULL COMMENT '标准错误尾部摘要',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted_at` DATETIME(3) NULL COMMENT '逻辑删除时间',

  CONSTRAINT `uk_creation_ffmpeg_command_log_biz_id` UNIQUE (`biz_id`),
  KEY `idx_creation_ffmpeg_command_project_version` (`project_id`, `version_id`),
  KEY `idx_creation_ffmpeg_command_match_id` (`match_id`),
  KEY `idx_creation_ffmpeg_command_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='创作链路FFmpeg命令审计日志表';
