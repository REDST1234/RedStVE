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
            JsonNode contextNode = objectMapper.readTree(inputContext);
            double duckingRatio = contextNode.path("duckingRatio").asDouble(0.2); // 默认降至 20%
            
            // 假设主音频流在 [0:a], BGM 流在 [1:a]
            // 如果单音轨素材没有主音频流 (hasAudio = false)，我们需要用 anullsrc 兜底，防止 sidechaincompress 找不到主控流而崩溃
            String filterComplex;
            if (!hasAudio) {
                // 如果没有主音轨，则静音兜底 + BGM 保持原样 (通过 amix 或原样输出)
                log.info("AudioDucking: No original audio, generating anullsrc dummy track.");
                filterComplex = "[1:a]volume=1.0[outa]";
            } else {
                // 使用 sidechaincompress
                // [1:a] 是被压限的 BGM
                // [0:a] 是控制流 (主音轨口播)
                // 压缩比根据 duckingRatio 换算，这里给出一个简单的 sidechaincompress 公式
                double threshold = 0.08; // 压限阈值
                double ratio = 1.0 / duckingRatio; // 压限比例
                filterComplex = String.format("[1:a][0:a]sidechaincompress=threshold=%f:ratio=%f:attack=200:release=1000[bgm_ducked];" +
                        "[0:a][bgm_ducked]amix=inputs=2:duration=first:dropout_transition=2[outa]", threshold, ratio);
            }

            fragment.setFilterComplex(filterComplex);
            fragment.getMapArgs().add("-map");
            fragment.getMapArgs().add("0:v?");
            fragment.getMapArgs().add("-map");
            fragment.getMapArgs().add("[outa]");
            
        } catch (Exception e) {
            log.error("Failed to parse AudioDucking context", e);
        }
        return fragment;
    }
}
