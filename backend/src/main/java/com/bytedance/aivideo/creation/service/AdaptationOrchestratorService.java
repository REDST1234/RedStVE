package com.bytedance.aivideo.creation.service;

public interface AdaptationOrchestratorService {
    
    /**
     * 触发异步状态机进行素材适配 (FFmpeg 策略执行)
     * @param projectId 创作项目ID
     */
    String orchestrateAdaptation(String projectId, String versionId);

    /**
     * 针对单一槽位异步触发 AI 重绘
     * @param projectId 项目 ID
     * @param segmentIndex 段落索引
     * @param newPrompt 新的提示词（如果为空则使用原本的）
     */
    void regenerateImageAsync(String projectId, int segmentIndex, String newPrompt);
}
