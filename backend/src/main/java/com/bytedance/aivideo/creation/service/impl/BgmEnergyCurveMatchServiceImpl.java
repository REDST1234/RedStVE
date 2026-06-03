package com.bytedance.aivideo.creation.service.impl;

import com.bytedance.aivideo.creation.service.BgmEnergyCurveMatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class BgmEnergyCurveMatchServiceImpl implements BgmEnergyCurveMatchService {

    private final ObjectMapper objectMapper;

    public BgmEnergyCurveMatchServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public double calculateEnergyCurveScore(String audioDataJson, String templateJson) {
        if (audioDataJson == null || audioDataJson.isBlank() || templateJson == null || templateJson.isBlank()) {
            return 0.0;
        }

        try {
            JsonNode audioRoot = objectMapper.readTree(audioDataJson);
            JsonNode templateRoot = objectMapper.readTree(templateJson);

            // 1. Hard Veto 检测：声场环境与音乐风格冲突
            String templateAcousticEnv = templateRoot.path("meta").path("acousticEnvironment").asText("").toLowerCase(Locale.ROOT);
            String bgmStyle = audioRoot.path("globalMetrics").path("overallStyle").asText("").toLowerCase(Locale.ROOT);

            // 如果模板是极静的 ASMR 氛围，但 BGM 是重鼓点/快节奏类型，触发 Veto
            if ("asmr".equals(templateAcousticEnv)) {
                if (bgmStyle.contains("hip_hop") || bgmStyle.contains("trance") || bgmStyle.contains("rock") || bgmStyle.contains("electronic")) {
                    log.info("BGM Vetoed: ASMR template with loud BGM style: {}", bgmStyle);
                    return -1.0;
                }
            }

            // 2. 解析 BGM 时间轴
            double bgmDuration = audioRoot.path("globalMetrics").path("durationSeconds").asDouble(30.0);
            if (bgmDuration <= 0) bgmDuration = 30.0;

            List<BgmSlice> bgmSlices = new ArrayList<>();
            JsonNode timelineNode = audioRoot.path("auditoryTimeline");
            if (timelineNode.isArray()) {
                for (JsonNode sliceNode : timelineNode) {
                    double start = sliceNode.path("timeRange").path("start").asDouble(0.0);
                    double end = sliceNode.path("timeRange").path("end").asDouble(0.0);
                    int energy = sliceNode.path("auditoryPerception").path("energyLevel").asInt(5);
                    String slot = sliceNode.path("bestMatchedSlot").asText("").toLowerCase(Locale.ROOT);
                    bgmSlices.add(new BgmSlice(start, end, energy, slot));
                }
            }

            if (bgmSlices.isEmpty()) {
                return 0.5; // 无时间轴数据，返回中等分
            }

            // 3. 解析模板时间轴并计算累积时间
            JsonNode segmentsNode = templateRoot.path("scriptStructure").path("segments");
            if (!segmentsNode.isArray() || segmentsNode.isEmpty()) {
                return 1.0; // 模板无段落结构，默认不扣分
            }

            List<TemplateSegment> templateSegments = new ArrayList<>();
            double accumTime = 0.0;
            for (JsonNode segNode : segmentsNode) {
                double minDur = segNode.path("durationRange").path("min").asDouble(3.0);
                if (minDur <= 0) minDur = 3.0;
                String role = segNode.path("role").asText("").toLowerCase(Locale.ROOT);
                
                double start = accumTime;
                double end = accumTime + minDur;
                double center = accumTime + (minDur / 2.0);
                
                templateSegments.add(new TemplateSegment(start, end, center, role));
                accumTime += minDur;
            }

            // 4. 对齐匹配
            double totalScoreSum = 0.0;
            int matchCount = 0;

            for (TemplateSegment seg : templateSegments) {
                // 根据绝对时间中心点查找对应的 BGM 切片（支持循环取模）
                double targetBgmTime = seg.center % bgmDuration;
                BgmSlice matchedSlice = findBgmSliceAtTime(bgmSlices, targetBgmTime);

                if (matchedSlice != null) {
                    // 计算角色匹配度
                    double roleScore = 0.5;
                    if (seg.role.equals(matchedSlice.slot)) {
                        roleScore = 1.0;
                    }

                    // 计算能量偏差扣分
                    double energyScore = calculateEnergyScore(seg.role, matchedSlice.energyLevel);

                    // 综合该段落的分数
                    double segmentMatchScore = (roleScore * 0.4) + (energyScore * 0.6);
                    totalScoreSum += segmentMatchScore;
                    matchCount++;
                }
            }

            double finalScore = matchCount > 0 ? (totalScoreSum / matchCount) : 0.5;
            log.info("BGM energy curve match score calculated: score={}, matchCount={}", finalScore, matchCount);
            return finalScore;

        } catch (Exception e) {
            log.error("Failed to calculate BGM energy curve score", e);
            return 0.0;
        }
    }

    private BgmSlice findBgmSliceAtTime(List<BgmSlice> slices, double time) {
        for (BgmSlice slice : slices) {
            if (time >= slice.start && time <= slice.end) {
                return slice;
            }
        }
        // 兜底返回最接近的
        if (!slices.isEmpty()) {
            return slices.get(slices.size() - 1);
        }
        return null;
    }

    private double calculateEnergyScore(String role, int energyLevel) {
        int minExpected = 4;
        int maxExpected = 7;

        switch (role) {
            case "hook":
                minExpected = 3;
                maxExpected = 6;
                break;
            case "body":
                minExpected = 5;
                maxExpected = 8;
                break;
            case "climax":
                minExpected = 7;
                maxExpected = 10;
                break;
            case "outro":
                minExpected = 2;
                maxExpected = 5;
                break;
            case "transition":
                minExpected = 3;
                maxExpected = 6;
                break;
        }

        if (energyLevel >= minExpected && energyLevel <= maxExpected) {
            return 1.0;
        }

        // 计算偏差距离
        int diff = 0;
        if (energyLevel < minExpected) {
            diff = minExpected - energyLevel;
        } else {
            diff = energyLevel - maxExpected;
        }

        // 偏差 1 级扣 0.15，2 级扣 0.3，依次类推，最低 0 分
        return Math.max(0.0, 1.0 - (diff * 0.15));
    }

    private static class BgmSlice {
        double start;
        double end;
        int energyLevel;
        String slot;

        BgmSlice(double start, double end, int energyLevel, String slot) {
            this.start = start;
            this.end = end;
            this.energyLevel = energyLevel;
            this.slot = slot;
        }
    }

    private static class TemplateSegment {
        double center;
        String role;

        TemplateSegment(double start, double end, double center, String role) {
            this.center = center;
            this.role = role;
        }
    }
}
