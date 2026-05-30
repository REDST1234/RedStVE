package com.bytedance.aivideo.engine.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 镜像平滑循环策略 (Loop Sequence)
 * 用于将极短的静态视频通过 Ping-Pong (reverse) 镜像循环加长，解决短视频素材复用时长不够的问题。
 */
@Slf4j
@Component
public class LoopSequenceExecutor implements StrategyExecutor {

    @Override
    public String getStrategyType() {
        return "LOOP_SEQUENCE";
    }

    @Override
    public StrategyExecutionFragment buildExecutionFragment(String inputContext, boolean hasAudio) {
        StrategyExecutionFragment fragment = new StrategyExecutionFragment();
        fragment.setOutputKind(StrategyOutputKind.VIDEO_OUTPUT);
        fragment.setPreferredExtension("mp4");
        fragment.setPreserveAudio(false);
        // 核心思路：将输入源分为正向和反向两份，然后拼接
        // [0:v]reverse[r];[0:v][r]concat=n=2:v=1:a=0[outv]
        fragment.setFilterComplex("[0:v]reverse[r];[0:v][r]concat=n=2:v=1:a=0[outv]");
        fragment.getMapArgs().add("-map");
        fragment.getMapArgs().add("[outv]");
        fragment.getExtraArgs().add("-an");
        return fragment;
    }
}
