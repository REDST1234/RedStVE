ALTER TABLE `creation_project`
  ADD COLUMN `render_aspect_ratio` VARCHAR(16) NULL COMMENT '上次生成时保存的目标画面比例' AFTER `status`;
