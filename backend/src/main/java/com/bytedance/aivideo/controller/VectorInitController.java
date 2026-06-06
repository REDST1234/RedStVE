package com.bytedance.aivideo.controller;

import com.bytedance.aivideo.common.api.ApiResponse;
import com.bytedance.aivideo.infrastructure.vector.VectorInitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 向量数据初始化接口（手动触发）。
 * <p>
 * 不再在应用启动时自动刷入；改为通过本接口或 scripts/vector-init.sh 脚本手动调用。
 */
@Slf4j
@RestController
public class VectorInitController {

    private final VectorInitService vectorInitService;

    public VectorInitController(VectorInitService vectorInitService) {
        this.vectorInitService = vectorInitService;
    }

    @PostMapping("/api/v1/admin/vector/init")
    public ApiResponse<Map<String, Object>> initVectors() {
        log.info("收到手动向量初始化请求...");
        Map<String, Object> result = vectorInitService.syncAll();
        log.info("向量初始化完成: {}", result);
        return ApiResponse.success(result);
    }
}
