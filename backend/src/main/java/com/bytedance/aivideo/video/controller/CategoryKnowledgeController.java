package com.bytedance.aivideo.video.controller;

import com.bytedance.aivideo.common.api.ApiResponse;
import com.bytedance.aivideo.video.dto.CategoryKnowledgeGraphDto;
import com.bytedance.aivideo.video.service.CategoryKnowledgeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryKnowledgeController {

    private final CategoryKnowledgeService categoryKnowledgeService;

    public CategoryKnowledgeController(CategoryKnowledgeService categoryKnowledgeService) {
        this.categoryKnowledgeService = categoryKnowledgeService;
    }

    /**
     * 获取品类知识库图谱数据（包含品类大节点及对应的模板小节点）
     */
    @GetMapping("/graph-data")
    public ApiResponse<CategoryKnowledgeGraphDto> getGraphData() {
        return ApiResponse.success(categoryKnowledgeService.getCategoryKnowledgeGraph());
    }
}
