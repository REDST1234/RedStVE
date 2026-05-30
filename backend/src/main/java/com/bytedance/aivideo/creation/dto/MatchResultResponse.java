package com.bytedance.aivideo.creation.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 槽位匹配结果响应。
 */
@Data
public class MatchResultResponse {

    private String projectId;
    private String versionId;
    private String status;
    private Double overallCoverage;
    private List<MatchResultItemResponse> items = new ArrayList<>();
}
