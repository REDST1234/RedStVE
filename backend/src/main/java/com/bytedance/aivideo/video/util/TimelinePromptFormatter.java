package com.bytedance.aivideo.video.util;

import com.bytedance.aivideo.video.dto.timeline.AudioTextEntry;
import com.bytedance.aivideo.video.dto.timeline.KeyFrameEntry;
import com.bytedance.aivideo.video.dto.timeline.TimelineMatchResult;
import com.bytedance.aivideo.video.dto.timeline.TimelineSegment;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

/**
 * 将 TimelineMatchResult 胖 JSON 降维转换为 Markdown 剧本流文本，
 * 供大模型阅读，大幅降低 token 消耗并提升阅读理解能力。
 */
public class TimelinePromptFormatter {

    public static String formatToMarkdown(TimelineMatchResult result) {
        return formatToMarkdown(result, Collections.emptyMap());
    }

    /**
     * @param imageTagByPath 键: 关键帧绝对路径，值: 图片标签（如【IMAGE_001】），标签顺序需与发送给模型的图片顺序一致。
     */
    public static String formatToMarkdown(TimelineMatchResult result, Map<String, String> imageTagByPath) {
        if (result == null || result.getTimelineSegments() == null) {
            return "无时序数据";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("--- 视频多模态时序日志 (Markdown 剧本格式) ---\n\n");
        sb.append("说明: 关键帧标签【IMAGE_XXX】与多模态输入图片顺序严格一致。\n");
        if (result.getSystemMeta() != null && result.getSystemMeta().getVisualWaveform() != null) {
            sb.append("【全局画面变动波形】: 视频被 10 等分，数值越大代表该区间画面切分越剧烈\n");
            sb.append(result.getSystemMeta().getVisualWaveform().toString()).append("\n");
        }
        sb.append("\n");

        for (int i = 0; i < result.getTimelineSegments().size(); i++) {
            TimelineSegment seg = result.getTimelineSegments().get(i);
            sb.append(String.format("▶ [%s] 阶段: %s\n", seg.getTimeRange(), seg.getPhaseHint() != null ? seg.getPhaseHint() : "未知"));
            
            // 视听共振
            if (seg.getAudioVisualResonance() != null && !seg.getAudioVisualResonance().isBlank()) {
                sb.append("  - 视听共振: ").append(seg.getAudioVisualResonance()).append("\n");
            }
            
            // 视觉动作
            if (seg.getVisualDynamics() != null) {
                String visualStr = String.format("  - 画面: 发生%d次切分，切分速率(%.2f/s)", 
                        seg.getVisualDynamics().getRawCutsCount(), 
                        seg.getVisualDynamics().getCuttingVelocity());
                if (seg.getVisualDynamics().getTranslatedAction() != null) {
                    visualStr += "，推测: " + seg.getVisualDynamics().getTranslatedAction();
                }
                sb.append(visualStr).append("\n");
            }

            // 关键帧索引（按发送给模型的图片顺序标注）
            if (seg.getKeyFrames() != null && !seg.getKeyFrames().isEmpty()) {
                sb.append("  - 关键帧:\n");
                for (KeyFrameEntry frame : seg.getKeyFrames()) {
                    String imageTag = resolveImageTag(frame, imageTagByPath);
                    String timestamp = frame.getTimestamp() != null ? frame.getTimestamp() : "未知时间";
                    String frameRole = frame.getFrameRole() != null ? frame.getFrameRole() : "UNKNOWN";
                    if (imageTag != null) {
                        sb.append(String.format("    > %s @ %s (角色:%s)\n", imageTag, timestamp, frameRole));
                    } else {
                        sb.append(String.format("    > [UNSENT_IMAGE] @ %s (角色:%s)\n", timestamp, frameRole));
                    }
                }
            }
            
            // 语音/文案
            if (seg.getAudioAndText() != null && !seg.getAudioAndText().isEmpty()) {
                sb.append("  - 声音:\n");
                for (AudioTextEntry audio : seg.getAudioAndText()) {
                    sb.append(String.format("    > \"%s\" (情绪:%s | 音量:%s | 背景:%s)\n", 
                            audio.getText(),
                            audio.getAudioEmotion() != null ? audio.getAudioEmotion() : "NORMAL",
                            audio.getVolumeIntensity() != null ? audio.getVolumeIntensity() : "NORMAL",
                            audio.getBackgroundEnvironment() != null ? audio.getBackgroundEnvironment() : "CLEAN"
                    ));
                }
            }
            
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String resolveImageTag(KeyFrameEntry frame, Map<String, String> imageTagByPath) {
        if (frame == null || frame.getFilePath() == null || frame.getFilePath().isBlank() || imageTagByPath == null || imageTagByPath.isEmpty()) {
            return null;
        }
        String direct = imageTagByPath.get(frame.getFilePath());
        if (direct != null) {
            return direct;
        }
        try {
            Path normalizedPath = Paths.get(frame.getFilePath()).toAbsolutePath().normalize();
            return imageTagByPath.get(normalizedPath.toString());
        } catch (Exception ignored) {
            return null;
        }
    }
}
