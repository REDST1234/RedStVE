package com.bytedance.aivideo.engine.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 智能闪避/压限策略
 * 作用：当口播响起时，BGM 自动降低音量；口播结束，BGM 音量恢复。
 */
@Slf4j
@Component
public class AudioDuckingExecutor implements StrategyExecutor {

    private final ObjectMapper objectMapper;

    public AudioDuckingExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStrategyType() {
        return "AUDIO_DUCKING";
    }

    @Override
    public StrategyExecutionFragment buildExecutionFragment(String inputContext, boolean hasAudio) {
        StrategyExecutionFragment fragment = new StrategyExecutionFragment();
        fragment.setOutputKind(StrategyOutputKind.VIDEO_OUTPUT);
        fragment.setPreferredExtension("mp4");
        fragment.setPreserveAudio(true);
        try {

            // 假设主音频流在 [0:a], BGM 流在 [1:a]
            if (!hasAudio) {
                // 如果没有主音轨，则静音兜底
                log.info("AudioDucking: No original audio, generating anull dummy track or skipping.");
                fragment.getAudioFilters().add("volume=1.0");
            } else {
                // 修复: 目前在分段视频适配阶段（AdaptationOrchestrator），并没有传入全局的 BGM 文件作为第二个输入参数（即没有 [1:a]）。
                // 所以 FFmpeg 会报 "Invalid file index 1 in filtergraph"。
                // 真正的 BGM 压混逻辑应该留到最终生成时间线（Timeline）或者 Remotion 渲染时处理。
                // 此处我们将其降级为无副作用的 NO-OP 滤镜，以保证编排器能顺利通过校验并产出中间文件。
                log.info("AudioDucking: Deferring to final composition phase. Adding NO-OP filter.");
                fragment.getAudioFilters().add("volume=1.0");
            }
            
        } catch (Exception e) {
            log.error("Failed to parse AudioDucking context", e);
        }
        return fragment;
    }
}
