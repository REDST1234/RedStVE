package com.bytedance.aivideo.engine.ffmpeg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 镜头数据模型。
 * 记录 FFmpeg 拆解出的单个连续镜头片段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneShot {
    
    /**
     * 镜头序号（0-indexed）
     */
    private Integer shotIndex;
    
    /**
     * 镜头起始时间（秒）
     */
    private Double startTime;
    
    /**
     * 镜头结束时间（秒）
     */
    private Double endTime;
    
    /**
     * 镜头持续时长（秒）
     */
    private Double duration;
    
    /**
     * 镜头切分得分（发生该切分点时的 sceneScore）
     */
    private Double sceneScore;
    
    /**
     * 关键帧缩略图的本地绝对路径（后续流程生成）
     */
    private String thumbnailPath;
}
