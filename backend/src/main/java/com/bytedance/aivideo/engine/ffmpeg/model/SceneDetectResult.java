package com.bytedance.aivideo.engine.ffmpeg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 镜头切分探测结果模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneDetectResult {
    
    /**
     * 切分出的镜头列表
     */
    private List<SceneShot> shots;
    
    /**
     * 总镜头数
     */
    private Integer totalShots;
}
