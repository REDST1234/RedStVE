package com.bytedance.aivideo.engine.remotion.dto;

import lombok.Data;

@Data
public class RenderTaskRequest {
    private String taskId;
    private com.bytedance.aivideo.creation.dto.remotion.CompositionScript compositionScript;
}
