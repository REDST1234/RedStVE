package com.bytedance.aivideo.creation.dto;

import lombok.Data;

@Data
public class ConfirmAssetsResponse {

    private String projectId;
    private Integer totalAssets;
    private Integer triggeredCount;
    private String status;
}
