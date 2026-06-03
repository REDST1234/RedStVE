package com.bytedance.aivideo.engine.remotion.dto;

import com.bytedance.aivideo.creation.dto.remotion.CompositionScript;
import lombok.Data;

@Data
public class VideoOrchestrationResult {
    private CompositionScript script;
    private String rawContent;
    private String reasoningContent;
}
