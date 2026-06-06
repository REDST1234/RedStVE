package com.bytedance.aivideo.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bytedance.aivideo.deconstruct.entity.DeconstructTemplateEntity;
import com.bytedance.aivideo.deconstruct.mapper.DeconstructTemplateMapper;
import com.bytedance.aivideo.video.dto.CategoryKnowledgeGraphDto;
import com.bytedance.aivideo.video.entity.CategoryKnowledgeEntity;
import com.bytedance.aivideo.video.mapper.CategoryKnowledgeMapper;
import com.bytedance.aivideo.video.service.CategoryKnowledgeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CategoryKnowledgeServiceImpl extends ServiceImpl<CategoryKnowledgeMapper, CategoryKnowledgeEntity> implements CategoryKnowledgeService {

    private final CategoryKnowledgeMapper categoryKnowledgeMapper;
    private final DeconstructTemplateMapper deconstructTemplateMapper;

    public CategoryKnowledgeServiceImpl(CategoryKnowledgeMapper categoryKnowledgeMapper, DeconstructTemplateMapper deconstructTemplateMapper) {
        this.categoryKnowledgeMapper = categoryKnowledgeMapper;
        this.deconstructTemplateMapper = deconstructTemplateMapper;
    }

    @Override
    public CategoryKnowledgeGraphDto getCategoryKnowledgeGraph() {
        CategoryKnowledgeGraphDto dto = new CategoryKnowledgeGraphDto();

        // 1. Fetch all category knowledge
        List<CategoryKnowledgeEntity> categories = categoryKnowledgeMapper.selectList(new LambdaQueryWrapper<>());

        // 2. Fetch all templates
        List<DeconstructTemplateEntity> templates = deconstructTemplateMapper.selectList(new LambdaQueryWrapper<>());

        // Filter templates: keep only the latest version for each templateId
        Map<String, DeconstructTemplateEntity> latestMap = new HashMap<>();
        for (DeconstructTemplateEntity tpl : templates) {
            String tplId = tpl.getTemplateId();
            if (!latestMap.containsKey(tplId) || latestMap.get(tplId).getTemplateVersion() < tpl.getTemplateVersion()) {
                latestMap.put(tplId, tpl);
            }
        }
        List<DeconstructTemplateEntity> latestTemplates = new ArrayList<>(latestMap.values());

        dto.setCategories(categories);
        dto.setTemplates(latestTemplates);

        return dto;
    }
}
