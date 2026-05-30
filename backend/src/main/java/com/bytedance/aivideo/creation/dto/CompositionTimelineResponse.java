package com.bytedance.aivideo.creation.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 编排时间线响应。
 */
@Data
public class CompositionTimelineResponse {

    private String projectId;
    private String versionId;
    private String status;
    private List<TimelineSegmentResponse> segments = new ArrayList<>();
}
