package com.bytedance.aivideo.creation.service;

public interface AdaptationOrchestratorService {
    
    /**
     * 触发异步状态机进行素材适配 (FFmpeg 策略执行)
     * @param projectId 创作项目ID
     */
    void orchestrateAdaptation(String projectId);
}
