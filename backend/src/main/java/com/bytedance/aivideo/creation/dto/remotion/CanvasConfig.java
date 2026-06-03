package com.bytedance.aivideo.creation.dto.remotion;

import lombok.Data;

@Data
public class CanvasConfig {
    private Integer width;
    private Integer height;
    private Integer fps = 30;
}
