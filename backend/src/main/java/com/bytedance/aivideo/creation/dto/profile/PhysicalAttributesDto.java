package com.bytedance.aivideo.creation.dto.profile;

import lombok.Data;

/**
 * Layer 1: 物理属性层
 */
@Data
public class PhysicalAttributesDto {
    // 通用
    private Long fileSize;
    
    // 视频/音频通用
    private Double totalDurationSeconds;
    
    // 视频/图片通用
    private String resolution;
    private String aspectRatio;
    
    // 视频专有
    private Integer fps;
    private Boolean hasAudio;
    private LightingProfileDto lighting;
    
    // 文本专有
    private Integer charCount;
    private Integer paragraphCount;
}
