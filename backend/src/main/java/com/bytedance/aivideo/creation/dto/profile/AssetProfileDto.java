package com.bytedance.aivideo.creation.dto.profile;

import lombok.Data;
import java.util.List;

/**
 * 资产三层协议档案根节点
 */
@Data
public class AssetProfileDto {
    private Long bizId;
    private String materialType; // VIDEO, IMAGE, TEXT, AUDIO
    private String sourcePath;
    
    // Layer 1: 物理属性层
    private PhysicalAttributesDto physicalAttributes;
    
    // Layer 2: 语义标签层
    private SemanticTagsDto semanticTags;
    
    // Layer 3: 高光片段层
    private List<HighlightSegmentDto> highlights;
}
