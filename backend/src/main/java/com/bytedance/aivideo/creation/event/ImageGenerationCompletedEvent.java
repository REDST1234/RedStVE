package com.bytedance.aivideo.creation.event;

import org.springframework.context.ApplicationEvent;

/**
 * 当 Seedream 图片生成完成（成功或失败）时发布的事件。
 */
public class ImageGenerationCompletedEvent extends ApplicationEvent {

    private final String projectId;
    private final String versionId;
    private final String matchId;
    private final Integer segmentIndex;
    private final boolean success;
    private final String imageUrl;
    private final String errorMessage;

    public ImageGenerationCompletedEvent(
            Object source,
            String projectId,
            String versionId,
            String matchId,
            Integer segmentIndex,
            boolean success,
            String imageUrl,
            String errorMessage
    ) {
        super(source);
        this.projectId = projectId;
        this.versionId = versionId;
        this.matchId = matchId;
        this.segmentIndex = segmentIndex;
        this.success = success;
        this.imageUrl = imageUrl;
        this.errorMessage = errorMessage;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getVersionId() {
        return versionId;
    }

    public String getMatchId() {
        return matchId;
    }

    public Integer getSegmentIndex() {
        return segmentIndex;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
