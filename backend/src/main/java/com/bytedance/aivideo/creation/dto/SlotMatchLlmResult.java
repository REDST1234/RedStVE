package com.bytedance.aivideo.creation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SlotMatchLlmResult {

    private String projectId;

    private String versionId;

    private Double overallCoverage;

    private String gapSummary;

    private List<Decision> matchDecisions = new ArrayList<>();

    @Data
    public static class Decision {
        private Integer segmentIndex;
        private String segmentRole;
        private String matchedAssetId;
        private String matchedHighlightId;
        private Double matchScore;
        private String matchStatus;
        private String vetoReason;
        private String matchReason;
        private JsonNode adaptationPlan;

        // ---- AI 生图补位字段（仅 MISSING 状态时有效） ----
        /** LLM 判定该缺失素材适合由 Seedream 生成 */
        private Boolean imageGenEligible;
        /** 生图类别: UI_ELEMENT | STICKER | LOGO | ILLUSTRATION | BACKGROUND | NONE */
        private String imageGenCategory;
        /** Seedream 英文提示词（含透明背景等要求） */
        private String imageGenPrompt;
        /** 生成图片的自然语言描述（用于注入编排 LLM 的 assetBrief） */
        private String imageGenDescription;

        public boolean isImageGenEligible() {
            return Boolean.TRUE.equals(imageGenEligible)
                    && imageGenPrompt != null && !imageGenPrompt.isBlank();
        }
    }
}
