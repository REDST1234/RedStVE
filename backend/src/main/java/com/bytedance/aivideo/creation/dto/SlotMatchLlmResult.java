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
    }
}
