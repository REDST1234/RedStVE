package com.bytedance.aivideo.creation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BgmRecommendItemResponse {
    private Integer rank;
    private String audioId;
    private String audioName;
    private Integer bpm;
    private String overallStyle;
    private Double durationSeconds;
    private String filePath; // 相对音频文件夹或库的路径

    // 得分明细
    private Double semanticScore;
    private Double energyCurveScore;
    private Double durationBpmScore;
    private Double finalScore;
}
