package com.bytedance.aivideo.creation.dto.profile;

import lombok.Data;
import java.util.List;

/**
 * 创作流专属轻量级原声转写与环境音提取结果
 */
@Data
public class CreationAsrResult {
    /**
     * 提取出的人声全局台词汇总
     */
    private String fullText;
    
    /**
     * 带有时间轴的声音事件片段
     */
    private List<CreationAsrSegment> segments;
}
