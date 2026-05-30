package com.bytedance.aivideo.creation.dto;

import lombok.Data;

/**
 * 触发槽位匹配响应。
 */
@Data
public class MatchTriggerResponse {

    private String projectId;
    private String versionId;
    private String status;
    private Integer matchedSegmentCount;
    private Integer missingSegmentCount;
}
