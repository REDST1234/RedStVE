package com.bytedance.aivideo.video.service;

import com.bytedance.aivideo.video.dto.timeline.TimelineMatchResult;
import com.bytedance.aivideo.video.entity.KeyFrameEntity;

import java.util.List;

public interface StructureAnalyzerService {
    
    /**
     * 根据 Timeline 胖数据与视频关键帧进行大模型统一多模态认知分析。
     * 返回提纯后的 Timeline 以及大一统的结构模板 JSON。
     * 
     * @param taskId 任务ID
     * @param fatTimelineResult 未提纯的高敏多模态时序对齐结果
     * @param keyFrames 该视频的关键帧列表
     * @return 提纯后的清洗 Timeline 以及大模型输出的结果化模板
     */
    @lombok.Data
    @lombok.Builder
    class AnalysisOutput {
        private String videoStructureTemplateJson;
        private TimelineMatchResult refinedTimeline;
    }

    AnalysisOutput analyzeAndRefine(String taskId, TimelineMatchResult fatTimelineResult, List<KeyFrameEntity> keyFrames);

    /**
     * 一键执行全链路：获取胖数据、关键帧 -> 调用大模型 -> 返回结果并落库
     */
    AnalysisOutput triggerLlmAnalysis(String taskId);
}
