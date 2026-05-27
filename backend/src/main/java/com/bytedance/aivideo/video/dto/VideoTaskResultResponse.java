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

    private Object fatTimeline;

    private Object refinedTimeline;

    private Object videoStructureTemplate;

    private String categoryId;

    private List<String> partialFailedDimensions;

    private List<TaskStageDto> stages;

    @Data
    public static class TranscriptItem {
        private Double startTime;
        private Double endTime;
        private String text;
        private String speaker;
        private Double confidence;
        private String audioEmotion;
        private String volumeIntensity;
        private String backgroundEnvironment;
        private String vocalVibe;
        private String bgmGenre;
        private String bgmInstruments;
    }

}
