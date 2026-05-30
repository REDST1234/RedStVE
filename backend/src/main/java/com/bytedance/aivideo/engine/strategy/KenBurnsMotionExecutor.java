package com.bytedance.aivideo.engine.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 伪 3D 运镜策略 (Ken Burns Effect)
 * 内置推、拉、左、右 4 种硬编码安全模板，将静态图片赋予动态运镜，禁绝 LLM 动态计算复杂的 zoompan 公式
 */
@Slf4j
@Component
public class KenBurnsMotionExecutor implements StrategyExecutor {

    private final ObjectMapper objectMapper;

    public KenBurnsMotionExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStrategyType() {
        return "KEN_BURNS_MOTION";
    }

    @Override
    public StrategyExecutionFragment buildExecutionFragment(String inputContext, boolean hasAudio) {
        StrategyExecutionFragment fragment = new StrategyExecutionFragment();
        fragment.setLoopImageInput(true);
        fragment.setOutputKind(StrategyOutputKind.VIDEO_OUTPUT);
        fragment.setPreferredExtension("mp4");
        fragment.setPreserveAudio(false);
        try {
            JsonNode contextNode = objectMapper.readTree(inputContext);
            String direction = contextNode.path("direction").asText("ZOOM_IN");
            int durationFrames = contextNode.path("durationFrames").asInt(150); // 默认 5 秒(30fps)

            String zoomPanExpr;
            switch (direction.toUpperCase()) {
                case "ZOOM_OUT":
                    zoomPanExpr = String.format("zoompan=z='min(max(zoom-0.0015,1.0),1.5)':d=%d", durationFrames);
                    break;
                case "PAN_LEFT":
                    zoomPanExpr = String.format("zoompan=z=1.2:x='min(max(x-1,0),iw)':y='ih/2':d=%d", durationFrames);
                    break;
                case "PAN_RIGHT":
                    zoomPanExpr = String.format("zoompan=z=1.2:x='min(max(x+1,0),iw)':y='ih/2':d=%d", durationFrames);
                    break;
                case "ZOOM_IN":
                default:
                    // 默认缓慢推镜头 (Zoom in)
                    zoomPanExpr = String.format("zoompan=z='min(zoom+0.0015,1.5)':d=%d", durationFrames);
                    break;
            }

            fragment.getVideoFilters().add(zoomPanExpr);
            fragment.getExtraArgs().add("-frames:v");
            fragment.getExtraArgs().add(String.valueOf(durationFrames));
            fragment.getExtraArgs().add("-r");
            fragment.getExtraArgs().add("30");
            fragment.getExtraArgs().add("-pix_fmt");
            fragment.getExtraArgs().add("yuv420p");
            fragment.getExtraArgs().add("-an");

        } catch (Exception e) {
            log.error("Failed to parse KenBurnsMotion context", e);
        }
        return fragment;
    }
}
