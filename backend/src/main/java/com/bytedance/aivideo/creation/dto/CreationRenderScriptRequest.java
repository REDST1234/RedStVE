package com.bytedance.aivideo.creation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class CreationRenderScriptRequest {
    private JsonNode compositionScript;
    private String aspectRatio;
}
