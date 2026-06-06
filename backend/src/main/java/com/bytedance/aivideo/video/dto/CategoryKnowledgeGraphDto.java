package com.bytedance.aivideo.video.dto;

import com.bytedance.aivideo.video.entity.CategoryKnowledgeEntity;
import com.bytedance.aivideo.deconstruct.entity.DeconstructTemplateEntity;
import lombok.Data;

import java.util.List;

@Data
public class CategoryKnowledgeGraphDto {
    private List<CategoryKnowledgeEntity> categories;
    private List<DeconstructTemplateEntity> templates;
}
