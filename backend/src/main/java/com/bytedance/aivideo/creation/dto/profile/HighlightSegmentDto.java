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
    // 结构评分刚性标签：STATIC|ZOOM_IN|ZOOM_OUT|PAN
    private String cameraMovementTag;
    // 结构评分刚性标签：CLOSE_UP|MID_SHOT|WIDE_SHOT
    private String shotTypeTag;
    // 展示/解释字段
    private String shotType;
    
    // 视听融合提取出的核心台词 (若有)
    private String spokenText; 
    
    // 🌟 局部自然语言听觉描述 (Early Fusion)
    private String audioContext;
    
    // 图片/视频通用: 空间锚点
    private SpatialAnchorDto spatialAnchor;
    
    // 文本素材专有
    private String textContent;
    private String textType; // title_overlay, normal_subtitle
}
