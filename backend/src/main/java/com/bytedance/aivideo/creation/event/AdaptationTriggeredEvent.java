package com.bytedance.aivideo.creation.event;

import org.springframework.context.ApplicationEvent;

/**
 * 触发素材适配编排事件
 */
public class AdaptationTriggeredEvent extends ApplicationEvent {

    private final String projectId;
    private final String versionId;

    public AdaptationTriggeredEvent(Object source, String projectId, String versionId) {
        super(source);
        this.projectId = projectId;
        this.versionId = versionId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getVersionId() {
        return versionId;
    }
}
