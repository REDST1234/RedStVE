package com.bytedance.aivideo.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.config.MediaUploadProperties;
import com.bytedance.aivideo.config.TimelineMatchProperties;
import com.bytedance.aivideo.engine.ffmpeg.model.SceneDetectResult;
import com.bytedance.aivideo.engine.ffmpeg.model.SceneShot;
import com.bytedance.aivideo.video.dto.timeline.*;
import com.bytedance.aivideo.video.entity.AsrSegmentEntity;
import com.bytedance.aivideo.video.entity.KeyFrameEntity;
import com.bytedance.aivideo.video.entity.VideoAnalysisTaskEntity;
import com.bytedance.aivideo.video.mapper.AsrSegmentMapper;
import com.bytedance.aivideo.video.mapper.KeyFrameMapper;
import com.bytedance.aivideo.video.mapper.VideoAnalysisTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * TimelineMatcher — 多路时间轴归一器。
 * 核心设计：单一方法 match(taskId, threshold) 被调用两次，实现"胖数据喂 LLM → 精修模板存 DB"的完美闭环。
 */
@Slf4j
@Service
public class TimelineMatcherService {

    private final AsrSegmentMapper asrSegmentMapper;
    private final KeyFrameMapper keyFrameMapper;
    private final VideoAnalysisTaskMapper taskMapper;
    private final MediaUploadProperties mediaUploadProperties;
    private final TimelineMatchProperties timelineMatchProperties;
    private final ObjectMapper objectMapper;

    public TimelineMatcherService(AsrSegmentMapper asrSegmentMapper,
                                  KeyFrameMapper keyFrameMapper,
                                  VideoAnalysisTaskMapper taskMapper,
                                  MediaUploadProperties mediaUploadProperties,
                                  TimelineMatchProperties timelineMatchProperties,
                                  ObjectMapper objectMapper) {
        this.asrSegmentMapper = asrSegmentMapper;
        this.keyFrameMapper = keyFrameMapper;
        this.taskMapper = taskMapper;
        this.mediaUploadProperties = mediaUploadProperties;
        this.timelineMatchProperties = timelineMatchProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行时间轴对齐与合并。
     *
     * @param taskId    任务 ID
     * @param threshold 场景切分合并阈值 (如果是第一遍传默认 0.15，如果是第二遍传 LLM 吐出的最佳阈值)
     * @return 完整时间轴协议
     */
    public TimelineMatchResult match(String taskId, double threshold) {
        // 1. 验证任务存在
        VideoAnalysisTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<VideoAnalysisTaskEntity>()
                .eq(VideoAnalysisTaskEntity::getTaskId, taskId)
                .isNull(VideoAnalysisTaskEntity::getDeletedAt));
        if (task == null) {
            log.warn("TimelineMatch failed: Task not found, taskId={}", taskId);
            return null;
        }

        // 2. 读取高敏全量 scene_result.json
        Path sceneJsonPath = Paths.get(mediaUploadProperties.getDirectory())
                .resolve(taskId).resolve("scene_result.json").normalize().toAbsolutePath();
        if (!Files.exists(sceneJsonPath)) {
            log.warn("TimelineMatch skipped: scene_result.json not found for taskId={}", taskId);
            return null;
        }

        SceneDetectResult sceneResult;
        try {
            sceneResult = objectMapper.readValue(sceneJsonPath.toFile(), SceneDetectResult.class);
        } catch (Exception e) {
            log.error("Failed to parse scene_result.json", e);
            throw new RuntimeException("Failed to parse scene_result.json", e);
        }

        List<SceneShot> rawShots = sceneResult.getShots();
        if (rawShots == null || rawShots.isEmpty()) {
            return new TimelineMatchResult();
        }

        double totalDuration = rawShots.get(rawShots.size() - 1).getEndTime();

        // 3. 查询 ASR 和 KeyFrame 数据库记录
        List<AsrSegmentEntity> asrSegments = asrSegmentMapper.selectList(new LambdaQueryWrapper<AsrSegmentEntity>()
                .eq(AsrSegmentEntity::getTaskId, taskId)
                .isNull(AsrSegmentEntity::getDeletedAt)
                .orderByAsc(AsrSegmentEntity::getStartTime));

        List<KeyFrameEntity> keyFrames = keyFrameMapper.selectList(new LambdaQueryWrapper<KeyFrameEntity>()
                .eq(KeyFrameEntity::getTaskId, taskId)
                .isNull(KeyFrameEntity::getDeletedAt)
                .orderByAsc(KeyFrameEntity::getTimePoint));

        // 4. 后置过滤 + 相邻合并 + 碎段提纯
        List<MergedSegment> mergedSegments = mergeWithThreshold(rawShots, threshold, totalDuration);
        mergedSegments = compactMicroSegments(mergedSegments, keyFrames);

        // 5. 遍历合并后的窗口，进行时序对齐组装
        List<TimelineSegment> timelineSegments = new ArrayList<>();
        
        // 计算最大平均得分用于推断 CLIMAX
        double maxAvgScore = mergedSegments.stream()
                .mapToDouble(this::calculateAvgScore)
                .max().orElse(0.0);

        for (int i = 0; i < mergedSegments.size(); i++) {
            MergedSegment seg = mergedSegments.get(i);
            
            boolean isLastSegment = i == mergedSegments.size() - 1;

            // 组装 AudioText（按 ASR 起点归属，防止跨段污染）
            List<AudioTextEntry> audioAndText = new ArrayList<>();
            for (AsrSegmentEntity asr : asrSegments) {
                if (asr.getStartTime() == null) {
                    continue;
                }
                double asrStart = asr.getStartTime().doubleValue();
                if (isTimeInSegment(asrStart, seg.startTime(), seg.endTime(), isLastSegment)) {
                    double asrEnd = asr.getEndTime() == null ? asrStart : asr.getEndTime().doubleValue();
                    audioAndText.add(AudioTextEntry.builder()
                            .text(asr.getText())
                            .timestamp(formatTimeRange(asrStart, asrEnd))
                            .audioEmotion(asr.getAudioEmotion())
                            .volumeIntensity(asr.getVolumeIntensity())
                            .backgroundEnvironment(asr.getBackgroundEnvironment())
                            .vocalVibe(asr.getVocalVibe())
                            .bgmGenre(asr.getBgmGenre())
                            .bgmInstruments(asr.getBgmInstruments())
                            .build());
                }
            }
            AudioSemanticSummary audioSemanticSummary = summarizeAudioSemantics(audioAndText);

            // 组装 KeyFrames（统一半开区间，最后一段右闭补边界）
            List<KeyFrameEntry> keyFrameEntries = new ArrayList<>();
            for (KeyFrameEntity kf : keyFrames) {
                if (kf.getTimePoint() == null) {
                    continue;
                }
                double tp = kf.getTimePoint().doubleValue();
                if (isTimeInSegment(tp, seg.startTime(), seg.endTime(), isLastSegment)) {
                    keyFrameEntries.add(KeyFrameEntry.builder()
                            .frameRole(mapFrameRole(kf.getExtractionReason()))
                            .timestamp(formatTime(tp))
                            .filePath(kf.getFilePath())
                            .build());
                }
            }

            // 生成 VisualDynamics
            double avgScore = calculateAvgScore(seg);
            double windowDuration = seg.endTime() - seg.startTime();
            double cuttingVelocity = windowDuration > 0 ? seg.rawCutsInWindow().size() / windowDuration : 0;
            VisualDynamics dynamics = VisualDynamics.builder()
                    .rawCutsCount(seg.rawCutsInWindow().size())
                    .cuttingVelocity(Math.round(cuttingVelocity * 100.0) / 100.0)
                    .avgSceneScore(Math.round(avgScore * 100.0) / 100.0) // 保留两位小数
                    .translatedAction(translateVisualAction(seg.rawCutsInWindow(), windowDuration))
                    .build();

            // 推断 phaseHint
            String phaseHint = inferPhaseHint(i, mergedSegments.size(), avgScore, maxAvgScore);

            // 视听共振度推断 (基于全局 asrSegments 时间交集，解决长音频跨碎段被过滤的问题)
            boolean isAudioStrong = asrSegments.stream().anyMatch(asr -> {
                if (asr.getStartTime() == null) return false;
                double asrStart = asr.getStartTime().doubleValue();
                double asrEnd = asr.getEndTime() == null ? asrStart : asr.getEndTime().doubleValue();
                boolean isOverlapping = asrStart < seg.endTime() && asrEnd > seg.startTime();
                
                return isOverlapping &&
                        "MUSIC_FX".equals(asr.getBackgroundEnvironment()) &&
                        ("HIGH".equals(asr.getVolumeIntensity()) || "PEAK".equals(asr.getVolumeIntensity()));
            });
            boolean isVisualStrong = dynamics.getRawCutsCount() >= 8 || avgScore >= 0.3;
            
            String resonance;
            if (isAudioStrong && isVisualStrong) {
                resonance = "强卡点共振 (视听双爆发)";
            } else if (isAudioStrong) {
                resonance = "中度卡点 (听觉高潮，但画面相对平稳)";
            } else if (isVisualStrong) {
                resonance = "中度卡点 (画面爆发，但缺乏音效配合)";
            } else {
                resonance = "无明显共振";
            }

            TimelineSegment timelineSeg = TimelineSegment.builder()
                    .segmentId(String.format("seg_%03d", i + 1))
                    .timeRange(formatTimeRange(seg.startTime(), seg.endTime()))
                    .phaseHint(phaseHint)
                    .audioVisualResonance(resonance)
                    .audioAndText(audioAndText)
                    .audioSemanticSummary(audioSemanticSummary)
                    .visualDynamics(dynamics)
                    .keyFrames(keyFrameEntries)
                    .build();
            
            timelineSegments.add(timelineSeg);
        }

        // 6. 组装最终结果
        TimelineMatchResult result = new TimelineMatchResult();
        TimelineMatchResult.SystemMeta meta = new TimelineMatchResult.SystemMeta();
        meta.setTotalDuration(totalDuration);
        meta.setAppliedSceneThreshold(threshold);
        meta.setTotalKeyFramesExtracted(keyFrames.size());
        
        // 提取 10 分桶画面变动波形 (Visual Waveform)
        List<Double> visualWaveform = new ArrayList<>(java.util.Collections.nCopies(10, 0.0));
        double bucketDuration = totalDuration / 10.0;
        if (bucketDuration > 0) {
            for (SceneShot shot : rawShots) {
                double centerTime = shot.getStartTime() + (shot.getDuration() / 2.0);
                int bucketIndex = (int) (centerTime / bucketDuration);
                if (bucketIndex >= 10) bucketIndex = 9;
                if (bucketIndex < 0) bucketIndex = 0;
                
                double score = shot.getSceneScore() != null ? shot.getSceneScore() : 0.0;
                visualWaveform.set(bucketIndex, visualWaveform.get(bucketIndex) + score);
            }
            // 结果保留两位小数
            for (int i = 0; i < visualWaveform.size(); i++) {
                visualWaveform.set(i, Math.round(visualWaveform.get(i) * 100.0) / 100.0);
            }
        }
        meta.setVisualWaveform(visualWaveform);
        
        result.setSystemMeta(meta);
        result.setTimelineSegments(timelineSegments);

        return result;
    }

    /**
     * 核心过滤算法：剔除伪切点，相邻合并为业务镜头。
     */
    private List<MergedSegment> mergeWithThreshold(List<SceneShot> rawShots, double threshold, double totalDuration) {
        List<MergedSegment> segments = new ArrayList<>();
        double segStart = 0.0;
        List<SceneShot> cutsInWindow = new ArrayList<>();

        for (SceneShot shot : rawShots) {
            cutsInWindow.add(shot);
            if (shot.getSceneScore() != null && shot.getSceneScore() >= threshold) {
                segments.add(new MergedSegment(segStart, shot.getEndTime(), cutsInWindow));
                segStart = shot.getEndTime();
                cutsInWindow = new ArrayList<>();
            }
        }
        
        // 闭合最后一个窗口
        if (!cutsInWindow.isEmpty() || segStart < totalDuration) {
            segments.add(new MergedSegment(segStart, totalDuration, cutsInWindow));
        }
        
        return segments;
    }

    /**
     * 对高敏模式切出的微碎段进行后置提纯：
     * 仅合并“短且无关键帧”的窗口，优先并入前一段。
     */
    private List<MergedSegment> compactMicroSegments(List<MergedSegment> segments, List<KeyFrameEntity> keyFrames) {
        if (segments == null || segments.size() <= 1) {
            return segments;
        }
        List<MergedSegment> compacted = new ArrayList<>(segments);
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < compacted.size(); i++) {
                if (compacted.size() <= 1) {
                    break;
                }
                MergedSegment current = compacted.get(i);
                double duration = current.endTime() - current.startTime();
                if (duration + timelineMatchProperties.getFloatEpsilon() >= timelineMatchProperties.getMinSegmentDurationSec()) {
                    continue;
                }
                boolean isLastSegment = i == compacted.size() - 1;
                if (containsKeyFrame(current, keyFrames, isLastSegment)) {
                    continue;
                }

                if (i > 0) {
                    MergedSegment prev = compacted.get(i - 1);
                    compacted.set(i - 1, mergeSegments(prev, current));
                    compacted.remove(i);
                } else {
                    MergedSegment next = compacted.get(i + 1);
                    compacted.set(i, mergeSegments(current, next));
                    compacted.remove(i + 1);
                }
                changed = true;
                break;
            }
        } while (changed);

        return compacted;
    }

    private boolean containsKeyFrame(MergedSegment segment, List<KeyFrameEntity> keyFrames, boolean isLastSegment) {
        if (keyFrames == null || keyFrames.isEmpty()) {
            return false;
        }
        for (KeyFrameEntity keyFrame : keyFrames) {
            if (keyFrame.getTimePoint() == null) {
                continue;
            }
            double timePoint = keyFrame.getTimePoint().doubleValue();
            if (isTimeInSegment(timePoint, segment.startTime(), segment.endTime(), isLastSegment)) {
                return true;
            }
        }
        return false;
    }

    private MergedSegment mergeSegments(MergedSegment left, MergedSegment right) {
        List<SceneShot> mergedCuts = new ArrayList<>();
        if (left.rawCutsInWindow() != null) {
            mergedCuts.addAll(left.rawCutsInWindow());
        }
        if (right.rawCutsInWindow() != null) {
            mergedCuts.addAll(right.rawCutsInWindow());
        }
        return new MergedSegment(left.startTime(), right.endTime(), mergedCuts);
    }

    private boolean isTimeInSegment(double time, double startInclusive, double end, boolean isLastSegment) {
        if (time + timelineMatchProperties.getFloatEpsilon() < startInclusive) {
            return false;
        }
        if (isLastSegment) {
            return time <= end + timelineMatchProperties.getFloatEpsilon();
        }
        return time < end - timelineMatchProperties.getFloatEpsilon();
    }

    private double calculateAvgScore(MergedSegment seg) {
        if (seg.rawCutsInWindow() == null || seg.rawCutsInWindow().isEmpty()) {
            return 0.0;
        }
        return seg.rawCutsInWindow().stream()
                .filter(Objects::nonNull)
                .mapToDouble(s -> s.getSceneScore() != null ? s.getSceneScore() : 0.0)
                .average().orElse(0.0);
    }

    /**
     * 三把斧翻译器：利用 CutsPerSecond 和 Score 计算出动态中文描述。
     */
    private String translateVisualAction(List<SceneShot> rawCuts, double windowDuration) {
        if (rawCuts == null || rawCuts.isEmpty()) {
            return "画面极度平稳，绝对固定机位或一镜到底";
        }
        int cutCount = rawCuts.size();
        double cutsPerSecond = windowDuration > 0 ? cutCount / windowDuration : 0;

        DoubleSummaryStatistics stats = rawCuts.stream()
                .mapToDouble(s -> s.getSceneScore() != null ? s.getSceneScore() : 0.0)
                .summaryStatistics();
        double avgScore = stats.getAverage();
        double maxScore = stats.getMax();

        if (cutCount == 0) {
            return "画面极度平稳，绝对固定机位或一镜到底";
        }
        if (cutsPerSecond >= 8.0 && avgScore < 0.15) {
            return String.format("视觉高敏区：短时间发生%d次微切分(%.1f次/秒)，推测为强烈手持晃动或推拉运镜 (avgScore=%.2f, maxScore=%.2f)",
                    cutCount, cutsPerSecond, avgScore, maxScore);
        }
        if (cutsPerSecond >= 4.0 && avgScore < 0.15) {
            return String.format("中等频率微切分(%d次, %.1f次/秒)，疑似缓速平移运镜或轻微手持抖动 (avgScore=%.2f)",
                    cutCount, cutsPerSecond, avgScore);
        }
        if (avgScore >= 0.3) {
            return String.format("发生强烈画面硬切转场 (avgScore=%.2f, maxScore=%.2f, 共%d次切分)",
                    avgScore, maxScore, cutCount);
        }
        if (avgScore >= 0.15) {
            return String.format("发生中等强度画面变动 (avgScore=%.2f, 共%d次切分, %.1f次/秒)",
                    avgScore, cutCount, cutsPerSecond);
        }
        return String.format("轻微画面变动(%d次微切分, avgScore=%.2f)", cutCount, avgScore);
    }

    private String inferPhaseHint(int index, int totalSegments, double avgScore, double maxAvgScore) {
        if (totalSegments <= 1) {
            return "BODY";
        }
        if (index == 0) {
            return "HOOK_CANDIDATE";
        }
        if (index == totalSegments - 1) {
            return "CTA_CANDIDATE";
        }
        // 如果是中间镜头中均分最高的，标记为高潮候选
        if (avgScore > 0 && Math.abs(avgScore - maxAvgScore) < 0.001) {
            return "BODY_CLIMAX";
        }
        return "BODY";
    }

    private String mapFrameRole(String extractionReason) {
        if (extractionReason == null) return "UNKNOWN";
        return switch (extractionReason) {
            case "HOOK_FIRST" -> "HOOK_START";
            case "HOOK_MID" -> "HOOK_MID";
            case "BOUNDARY_PRE" -> "CUT_BEFORE";
            case "BOUNDARY_POST" -> "CUT_AFTER";
            case "UNIFORM_SAMPLE" -> "UNIFORM_COVERAGE";
            case "LONG_SHOT_MID" -> "LONG_SHOT_MID";
            case "TOP_SCORE" -> "HIGH_SCORE_DYNAMIC";
            default -> extractionReason;
        };
    }

    private String formatTime(double time) {
        long minutes = (long) (time / 60);
        double seconds = time % 60;
        return String.format("%02d:%05.2f", minutes, seconds);
    }

    private String formatTimeRange(double start, double end) {
        return formatTime(start) + " - " + formatTime(end);
    }

    private AudioSemanticSummary summarizeAudioSemantics(List<AudioTextEntry> audioAndText) {
        return AudioSemanticSummary.builder()
                .vocalVibeSummary(pickDominantValue(audioAndText.stream().map(AudioTextEntry::getVocalVibe).toList()))
                .bgmGenreSummary(pickDominantValue(audioAndText.stream().map(AudioTextEntry::getBgmGenre).toList()))
                .bgmInstrumentsSummary(pickDominantValue(audioAndText.stream().map(AudioTextEntry::getBgmInstruments).toList()))
                .build();
    }

    private String pickDominantValue(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            counter.put(value, counter.getOrDefault(value, 0) + 1);
        }
        if (counter.isEmpty()) {
            return "";
        }
        String best = "";
        int max = 0;
        for (Map.Entry<String, Integer> entry : counter.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * 内部记录，用于保存合并后的时间窗口及其囊括的微切片。
     */
    private record MergedSegment(double startTime, double endTime, List<SceneShot> rawCutsInWindow) {}
}
