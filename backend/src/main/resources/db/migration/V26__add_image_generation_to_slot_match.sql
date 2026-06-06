ALTER TABLE `slot_match_result`
  ADD COLUMN `image_gen_eligible` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'LLM判定该缺失素材适合AI生图' AFTER `adapted_file_path`,
  ADD COLUMN `image_gen_category` VARCHAR(32) NULL COMMENT '生图类别: UI_ELEMENT/STICKER/LOGO/ILLUSTRATION/BACKGROUND/NONE' AFTER `image_gen_eligible`,
  ADD COLUMN `image_gen_prompt` TEXT NULL COMMENT 'Seedream生图提示词(英文)' AFTER `image_gen_category`,
  ADD COLUMN `image_gen_description` TEXT NULL COMMENT '生成图片的自然语言描述(注入编排LLM的assetBrief)' AFTER `image_gen_prompt`,
  ADD COLUMN `image_gen_status` VARCHAR(32) NULL COMMENT '生图状态: PENDING/PROCESSING/COMPLETED/FAILED' AFTER `image_gen_description`,
  ADD COLUMN `image_gen_url` VARCHAR(512) NULL COMMENT 'Seedream生成的图片公网URL' AFTER `image_gen_status`,
  ADD COLUMN `image_gen_error_message` TEXT NULL COMMENT '生图失败错误信息' AFTER `image_gen_url`;

CREATE INDEX `idx_slot_match_image_gen_status` ON `slot_match_result` (`image_gen_status`);
