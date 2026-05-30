package com.bytedance.aivideo.engine.strategy;

import java.util.List;

public interface StrategyExecutor {
    
    /**
     * @return 该执行器支持的策略类型标识 (如 TRIM_AND_CUT, AUDIO_DUCKING)
     */
    String getStrategyType();

    /**
     * 构建可被编排层聚合的 FFmpeg 处理片段。
     *
     * @param inputContext 策略执行上下文/参数 JSON
     * @param hasAudio     当前输入素材是否含有音频
     * @return 返回媒体处理片段，由编排器最终拼接成完整命令
     */
    StrategyExecutionFragment buildExecutionFragment(String inputContext, boolean hasAudio);
}
