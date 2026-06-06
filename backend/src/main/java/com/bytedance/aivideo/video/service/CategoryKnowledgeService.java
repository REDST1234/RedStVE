package com.bytedance.aivideo.video.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bytedance.aivideo.video.dto.CategoryKnowledgeGraphDto;
import com.bytedance.aivideo.video.entity.CategoryKnowledgeEntity;

public interface CategoryKnowledgeService extends IService<CategoryKnowledgeEntity> {

    /**
     * 获取分类及其关联模板的数据（用于展示关系图谱）
     * @return CategoryKnowledgeGraphDto
     */
    CategoryKnowledgeGraphDto getCategoryKnowledgeGraph();
}
