package com.bytedance.aivideo.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 视频拆解任务表实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_analysis_task")
public class VideoAnalysisTaskEntity extends BaseEntity {

    private String taskId;

    private Long sourceVideoBizId;

    private String status;

    private Integer progress;

    private String progressStep;

    private String sourceFilePath;

    /**
     * 提取后的音频文件路径（持久化保留）。
     */
    private String extractedAudioPath;

    private Long fileSize;

    private String errorMessage;

    private String errorStep;

    private Integer retryCount;

    private Integer priority;

    private String redisProgressKey;

    private LocalDateTime completedAt;
}
