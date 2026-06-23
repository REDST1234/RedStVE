package com.bytedance.aivideo.engine.remotion.dto;

import lombok.Data;

@Data
public class RenderTaskRequest {
    private String taskId;
    // 新增：用于死信退款
    private String userId;
    // 新增：用于死信退款知道退多少钱
    private Integer cost;
    private com.bytedance.aivideo.creation.dto.remotion.CompositionScript compositionScript;
}
