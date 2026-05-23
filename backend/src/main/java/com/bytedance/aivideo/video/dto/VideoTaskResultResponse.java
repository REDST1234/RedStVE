package com.bytedance.aivideo.video.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 任务结果响应 DTO。
 */
@Data
public class VideoTaskResultResponse {

    private String taskId;

    private String status;

    private Map<String, Object> videoInfo;

    private List<TranscriptItem> transcript;

    private Object timelineLog;

    private String categoryId;

    private List<String> partialFailedDimensions;

    private LlmAnalysis llmAnalysis;

    @Data
    public static class TranscriptItem {
        private Double startTime;
        private Double endTime;
        private String text;
        private String speaker;
        private Double confidence;
    }

    @Data
    public static class LlmAnalysis {
        private Object script;
        private Object rhythm;
        private Object packaging;
    }
}
