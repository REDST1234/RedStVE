package com.bytedance.aivideo.creation.dto;

import lombok.Data;

@Data
public class GenerateVideoRequest {
    /**
     * 目标画面比例，当前支持:
     * 9:16 / 16:9 / 1:1 / 4:5
     */
    private String aspectRatio;
}
