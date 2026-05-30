package com.bytedance.aivideo.creation.dto;

import lombok.Data;

/**
 * 编排时间线段落。
 */
@Data
public class TimelineSegmentResponse {

    private Integer segmentIndex;
    private String segmentRole;
    private String matchedAssetId;
    private String matchStatus;
    private String sourcePath;
    private String adaptedPath;
    private Double durationSeconds;
}
