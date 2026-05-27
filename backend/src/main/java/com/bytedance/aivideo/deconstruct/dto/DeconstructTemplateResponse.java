package com.bytedance.aivideo.deconstruct.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 全局模板响应。
 */
@Data
public class DeconstructTemplateResponse {

    private String templateId;

    private Integer templateVersion;

    private String templateName;

    private String categoryId;

    private String status;

    private String sourceTaskId;

    private String snapshotHash;

    private String templateJson;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
