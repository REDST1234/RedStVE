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

    /** 本地适配文件的可访问 URL（/api/storage/...），供前端直接展示 */
    private String adaptedFileUrl;

    // ---- AI 生图补位字段 ----
    private Boolean imageGenEligible;
    private String imageGenCategory;
    private String imageGenDescription;
    private String imageGenStatus;
    private String imageGenUrl;
    private String imageGenErrorMessage;
}
