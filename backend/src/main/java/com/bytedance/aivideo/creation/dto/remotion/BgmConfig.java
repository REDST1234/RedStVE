package com.bytedance.aivideo.creation.dto.remotion;

import lombok.Data;

@Data
public class BgmConfig {
    private String src;
    /**
     * 语义混音档位：由 LLM 输出，Java 侧映射为最终 volume。
     * 推荐枚举：QUIET / BALANCED / DRIVE
     */
    private String mixLevel;
    /**
     * 最终执行音量系数：由后端根据 mixLevel 计算。
     * 为兼容历史脚本，仍保留该字段。
     */
    private Double volume = 0.3;
    private Boolean loop = true;
    private Integer fadeInFrames = 15;
    private Integer fadeOutFrames = 30;
}
