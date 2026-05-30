package com.bytedance.aivideo.creation.dto;

import lombok.Data;

/**
 * 槽位匹配结果项。
 */
@Data
public class MatchResultItemResponse {

    private Integer segmentIndex;
    private String segmentRole;
    private String matchedAssetId;
    private String matchedHighlightId;
    private Double matchScore;
    private String matchStatus;
    private String matchReason;
    private String vetoReason;
    private String adaptationPlanJson;
    private String adaptedFilePath;
}
