package com.bytedance.aivideo.creation.dto.profile;

import lombok.Data;
import java.util.List;

/**
 * Layer 2: 语义标签层
 */
@Data
public class SemanticTagsDto {
    private List<String> mainEntities;
    private String lightVibe;
    private String overallStyle;
    private String audioVibe; // 融合对音频情绪的感知 (若有)
    private String audioDescription; // 🌟 全局自然语言听觉描述 (Early Fusion)
    private List<String> suitableRoles; // hook, body, climax, outro
    private String emotionTone;
    
    // 文本素材专有
    private String textCategory;
    private List<String> mainKeywords;
}
