package com.bytedance.aivideo.engine.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 裁剪与变速策略
 * 根据时间锚点截取视频，并按要求变速 (限速 0.5x ~ 2.0x，防止崩溃)
 */
@Slf4j
@Component
public class TrimAndCutExecutor implements StrategyExecutor {

    private final ObjectMapper objectMapper;

    public TrimAndCutExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStrategyType() {
        return "TRIM_AND_CUT";
    }

    @Override
    public StrategyExecutionFragment buildExecutionFragment(String inputContext, boolean hasAudio) {
        StrategyExecutionFragment fragment = new StrategyExecutionFragment();
        fragment.setOutputKind(StrategyOutputKind.VIDEO_OUTPUT);
        fragment.setPreferredExtension("mp4");
        fragment.setPreserveAudio(hasAudio);
        try {
            JsonNode contextNode = objectMapper.readTree(inputContext);
            double startTime = contextNode.path("startTime").asDouble(0.0);
            double endTime = contextNode.path("endTime").asDouble(0.0);
            double speed = contextNode.path("speed").asDouble(1.0);

            // 强制边界校验
            speed = Math.max(0.5, Math.min(2.0, speed));

            if (endTime > startTime) {
                fragment.getPreInputArgs().add("-ss");
                fragment.getPreInputArgs().add(String.valueOf(startTime));
                fragment.getPreInputArgs().add("-t");
                fragment.getPreInputArgs().add(String.valueOf(endTime - startTime));
            }

            if (Math.abs(speed - 1.0) > 0.01) {
                fragment.getVideoFilters().add(String.format("setpts=%f*PTS", 1.0 / speed));
                if (hasAudio) {
                    fragment.getAudioFilters().add(String.format("atempo=%f", speed));
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse TrimAndCut context", e);
        }
        return fragment;
    }
}
