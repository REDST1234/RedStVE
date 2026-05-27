package com.bytedance.aivideo.video.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务拆解阶段状态 DTO。
 */
@Data
public class TaskStageDto {
    private String stageType;
    private String stageStatus;
    private Integer stageProgress;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String errorMessage;
}
