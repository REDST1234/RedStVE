package com.bytedance.aivideo.creation.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class BgmRecommendResponse {
    private String projectId;
    private List<BgmRecommendItemResponse> recommendations;
}
