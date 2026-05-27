package com.bytedance.aivideo.deconstruct.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 模板快照响应。
 */
@Data
public class TemplateSnapshotDto {

    private String snapshotId;

    private String projectId;

    private String templateId;

    private Integer templateVersion;

    private String status;

    private String snapshotHash;

    private String sourceTaskId;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
