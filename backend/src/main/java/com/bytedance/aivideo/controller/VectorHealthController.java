package com.bytedance.aivideo.controller;

import com.bytedance.aivideo.common.api.ApiResponse;
import com.bytedance.aivideo.infrastructure.vector.VectorHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 向量基础设施健康检查接口。
 */
@RestController
public class VectorHealthController {

    private final VectorHealthService vectorHealthService;

    public VectorHealthController(VectorHealthService vectorHealthService) {
        this.vectorHealthService = vectorHealthService;
    }

    @GetMapping("/api/v1/system/vector/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(vectorHealthService.health());
    }
}
