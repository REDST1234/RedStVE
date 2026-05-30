package com.bytedance.aivideo.creation.dto.profile;

import lombok.Data;

/**
 * 带有时间轴的声音事件片段，支持可视化及辅助下游多模态模型融合。
 */
@Data
public class CreationAsrSegment {
    /**
     * 开始时间（秒）
     */
    private Double start;
    
    /**
     * 结束时间（秒）
     */
    private Double end;
    
    /**
     * 台词或自然语言环境音描述，例如："欢迎大家来到我的频道" 或 "(纸张摩擦声)"
     */
    private String text;
}
