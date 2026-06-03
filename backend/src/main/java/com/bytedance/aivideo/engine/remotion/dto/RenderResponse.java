package com.bytedance.aivideo.engine.remotion.dto;

import lombok.Data;

@Data
public class RenderResponse {
    private String taskId;
    private String status;
    private String error;
    private Double progress;
    private String outputPath;
}
