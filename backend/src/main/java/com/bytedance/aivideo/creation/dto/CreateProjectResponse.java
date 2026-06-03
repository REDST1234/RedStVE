package com.bytedance.aivideo.creation.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 新建创作项目响应。
 */
@Data
public class CreateProjectResponse {

    private String projectId;
    private String title;
    private String description;
    private String status;
    private String templateId;
    private String templateSnapshotId;
    private String aspectRatio;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
