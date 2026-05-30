package com.bytedance.aivideo.creation.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 创作项目列表项。
 */
@Data
public class CreationProjectListItemResponse {

    private String projectId;
    private String title;
    private String description;
    private String status;
    private String templateId;
    private String templateSnapshotId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
