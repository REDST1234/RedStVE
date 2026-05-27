package com.bytedance.aivideo.creation.dto.profile;

import lombok.Data;

/**
 * Layer 3: 高光片段层中的片段定义
 */
@Data
public class HighlightSegmentDto {
    private String segmentId;
    private Double usabilityScore;
    
    // 视频素材必有
    private TimeAnchorDto timeAnchor;
    
    // 动作与运镜
    private String actionState;
    private String cameraMovement;
    
    // 视听融合提取出的核心台词 (若有)
    private String spokenText; 
    
    // 图片/视频通用: 空间锚点
    private SpatialAnchorDto spatialAnchor;
    
    // 文本素材专有
    private String textContent;
    private String textType; // title_overlay, normal_subtitle
}
