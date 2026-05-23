package com.bytedance.aivideo.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 视频拆解任务阶段状态实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_analysis_task_stage")
public class VideoAnalysisTaskStageEntity extends BaseEntity {

    private String taskId;

    private String stageType;

    private String stageStatus;

    private Integer stageProgress;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;
}

