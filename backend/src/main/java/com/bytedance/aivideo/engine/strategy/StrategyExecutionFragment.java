package com.bytedance.aivideo.engine.strategy;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 策略执行器返回的媒体处理片段，供编排器聚合为最终 FFmpeg 命令。
 */
@Data
public class StrategyExecutionFragment {

    private List<String> preInputArgs = new ArrayList<>();

    private List<String> videoFilters = new ArrayList<>();

    private List<String> audioFilters = new ArrayList<>();

    private List<String> extraArgs = new ArrayList<>();

    private List<String> mapArgs = new ArrayList<>();

    private String filterComplex;

    private StrategyOutputKind outputKind = StrategyOutputKind.VIDEO_OUTPUT;

    private String preferredExtension = "mp4";

    private boolean loopImageInput;

    private boolean preserveAudio = true;

    public boolean hasExplicitFilterComplex() {
        return filterComplex != null && !filterComplex.isBlank();
    }

    public boolean hasSimpleFilters() {
        return !videoFilters.isEmpty() || !audioFilters.isEmpty();
    }
}
