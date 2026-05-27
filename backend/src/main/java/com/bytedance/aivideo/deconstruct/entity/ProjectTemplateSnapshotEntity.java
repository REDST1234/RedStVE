package com.bytedance.aivideo.deconstruct.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目模板快照实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_template_snapshot")
public class ProjectTemplateSnapshotEntity extends BaseEntity {

    private String snapshotId;

    private String projectId;

    private String templateId;

    private Integer templateVersion;

    private String status;

    private String sourceTaskId;

    private String snapshotJson;

    private String snapshotHash;
}
