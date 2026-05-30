package com.bytedance.aivideo.creation.dto;

import lombok.Data;

/**
 * 触发素材适配响应。
 */
@Data
public class AdaptTriggerResponse {

    private String projectId;
    private String versionId;
    private String status;
    private Integer adaptedCount;
    private Integer failedCount;
}
