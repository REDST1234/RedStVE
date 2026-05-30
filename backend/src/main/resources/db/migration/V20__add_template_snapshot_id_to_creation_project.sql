ALTER TABLE `creation_project`
    ADD COLUMN `template_snapshot_id` VARCHAR(64) NULL COMMENT '绑定的模板快照ID(project_template_snapshot.snapshot_id)' AFTER `template_id`;

CREATE INDEX idx_creation_project_snapshot_id ON `creation_project` (`template_snapshot_id`);
