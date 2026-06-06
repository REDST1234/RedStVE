package com.bytedance.aivideo.creation.service.impl;

import com.bytedance.aivideo.creation.entity.CreativeMaterialEntity;
import com.bytedance.aivideo.creation.service.TemplateStructureMatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class TemplateStructureMatchServiceImpl implements TemplateStructureMatchService {

    private static final String TAG_UNKNOWN = "UNKNOWN";
    private static final String TAG_STATIC = "STATIC";
    /** 品类: 动态图形/动画模板 — 不依赖实拍视频素材 */
    private static final String CATEGORY_MOTION_GRAPHICS = "motion_graphics";
    private static final double UNKNOWN_TAG_PENALTY = 0.1;
    private static final double GEOMETRIC_FLOOR = 0.01;
    private static final double MIN_SEGMENT_SCORE_THRESHOLD = 0.65;
    private static final double MIN_SEGMENT_SCORE_PENALTY_FACTOR = 0.5;

    private final ObjectMapper objectMapper;
    private final TemplateMatchCanonicalizer canonicalizer = new TemplateMatchCanonicalizer();

    public TemplateStructureMatchServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public double calculateStructureScore(List<CreativeMaterialEntity> materials, String templateJson) {
        return calculateStructureScore(materials, templateJson, "", null);
    }

    @Override
    public double calculateStructureScore(List<CreativeMaterialEntity> materials, String templateJson,
            String templateKey) {
        return calculateStructureScore(materials, templateJson, templateKey, null);
    }

    @Override
    public double calculateStructureScore(List<CreativeMaterialEntity> materials, String templateJson,
            String templateKey, String categoryId) {
        if (materials == null || materials.isEmpty() || templateJson == null || templateJson.isBlank()) {
            return 0.0;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(templateJson);
            TemplateMatchCanonicalizer.CanonicalTemplate template = canonicalizer.canonicalizeTemplate(rootNode);
            if (template.segments() == null || template.segments().isEmpty()) {
                return 0.5;
            }

            List<TemplateMatchCanonicalizer.CanonicalMaterial> materialProfiles = new ArrayList<>();
            int materialMissingTagCount = 0;
            int fallbackMappingCount = template.fallbackMappingCount();
            int unknownTagPenaltyCount = 0;
            String vetoReason = "NONE";
            boolean hasVideoMaterials = false;

            for (CreativeMaterialEntity material : materials) {
                if (!"PROFILED".equals(material.getStatus()) || material.getProfileJson() == null) {
                    continue;
                }
                if ("VIDEO".equalsIgnoreCase(material.getMaterialType())) {
                    hasVideoMaterials = true;
                }
                JsonNode profileNode = objectMapper.readTree(material.getProfileJson());
                TemplateMatchCanonicalizer.CanonicalMaterial canonicalMaterial = canonicalizer
                        .canonicalizeMaterial(material, profileNode);
                materialProfiles.add(canonicalMaterial);
                materialMissingTagCount += canonicalMaterial.missingTagCount();
                fallbackMappingCount += canonicalMaterial.fallbackMappingCount();
            }

            if (materialProfiles.isEmpty()) {
                return 0.0;
            }

            // MG模板：仅当用户没有上传任何视频素材时才跳过镜头运动/类型匹配
            final boolean mgSkipVideoChecks = CATEGORY_MOTION_GRAPHICS.equals(categoryId) && !hasVideoMaterials;

            List<Double> segmentBestScores = new ArrayList<>();
            int segmentCount = template.segments().size();

            for (TemplateMatchCanonicalizer.CanonicalSegment segment : template.segments()) {
                double bestMaterialScore = -1.0;
                int bestUnknownPenaltyCount = 0;

                for (TemplateMatchCanonicalizer.CanonicalMaterial profile : materialProfiles) {
                    SegmentEvaluation evaluation = evaluateMaterialForSegment(
                            profile,
                            segment,
                            template.acousticEnvironment(),
                            template.aspectRatio(),
                            mgSkipVideoChecks);
                    if (evaluation.score() > bestMaterialScore) {
                        bestMaterialScore = evaluation.score();
                        bestUnknownPenaltyCount = evaluation.unknownTagPenaltyCount();
                    }
                }

                if (bestMaterialScore < 0) {
                    vetoReason = "SEGMENT_HARD_VETO_" + segment.segmentIndex() + "_" + segment.role();
                    log.info("structure score vetoed: templateKey={}, vetoReason={}", templateKey, vetoReason);
                    return -1.0;
                }

                unknownTagPenaltyCount += bestUnknownPenaltyCount;
                segmentBestScores.add(bestMaterialScore);
            }

            double productOfScores = 1.0;
            double minSegmentScore = 1.0;
            for (double segmentScore : segmentBestScores) {
                double safeSegmentScore = Math.max(GEOMETRIC_FLOOR, segmentScore);
                productOfScores *= safeSegmentScore;
                if (segmentScore < minSegmentScore) {
                    minSegmentScore = segmentScore;
                }
            }
            double finalScore = Math.pow(productOfScores, 1.0 / segmentCount);
            if (minSegmentScore < MIN_SEGMENT_SCORE_THRESHOLD) {
                finalScore *= MIN_SEGMENT_SCORE_PENALTY_FACTOR;
            }

            // 正向惩罚：若项目中包含任何视频实拍素材，对纯 MG 动效模板给予 0.1 分的惩罚
            if (CATEGORY_MOTION_GRAPHICS.equals(categoryId) && hasVideoMaterials) {
                finalScore -= 0.1;
                finalScore = Math.max(0.0, finalScore);
                log.info("structure score penalized by 0.1 for MG template with video: templateKey={}", templateKey);
            }

            int templateTagSlots = segmentCount * 2;
            int materialTagSlots = materialProfiles.size() * 2;
            int totalTagSlots = Math.max(1, templateTagSlots + materialTagSlots);
            int missingTotal = template.missingTagCount() + materialMissingTagCount;
            double fieldCoverage = Math.max(0.0, 1.0 - ((double) missingTotal / totalTagSlots));

            if (fieldCoverage < 0.85) {
                log.warn("structure field coverage low: templateKey={}, fieldCoverage={}, threshold=0.85", templateKey,
                        fieldCoverage);
            }
            log.info(
                    "structure score computed: templateKey={}, templateMissingTagCount={}, materialMissingTagCount={}, fallbackMappingCount={}, unknownTagPenaltyCount={}, fieldCoverage={}, minSegmentScore={}, aggregation=GEOMETRIC_MEAN_WITH_MIN_PENALTY, vetoReason={}, score={}",
                    templateKey,
                    template.missingTagCount(),
                    materialMissingTagCount,
                    fallbackMappingCount,
                    unknownTagPenaltyCount,
                    fieldCoverage,
                    minSegmentScore,
                    vetoReason,
                    finalScore);
            return finalScore;

        } catch (Exception e) {
            log.error("计算结构匹配度异常: templateKey={}", templateKey, e);
            return 0.0;
        }
    }

    private SegmentEvaluation evaluateMaterialForSegment(
            TemplateMatchCanonicalizer.CanonicalMaterial profile,
            TemplateMatchCanonicalizer.CanonicalSegment segment,
            String templateAcousticEnv, String templateAspectRatio,
            boolean mgSkipVideoChecks) {

        double score = 1.0;
        int unknownTagPenaltyCount = 0;

        // ── 镜头运动匹配 ──
        // MG 模板无视频素材时跳过镜头运动匹配
        if (!mgSkipVideoChecks) {
            boolean needDynamic = !TAG_STATIC.equals(segment.cameraMovementTag())
                    && !TAG_UNKNOWN.equals(segment.cameraMovementTag());
            boolean isMaterialStatic = true;
            boolean isMaterialPureDynamic = true;
            boolean hasAnyHighlight = false;
            boolean materialMovementKnown = false;
            for (TemplateMatchCanonicalizer.CanonicalHighlight highlight : profile.highlights()) {
                hasAnyHighlight = true;
                String cm = highlight.cameraMovementTag();
                if (!TAG_UNKNOWN.equals(cm)) {
                    materialMovementKnown = true;
                }
                if (!TAG_STATIC.equals(cm) && !TAG_UNKNOWN.equals(cm)) {
                    isMaterialStatic = false;
                }
                if (TAG_STATIC.equals(cm) || TAG_UNKNOWN.equals(cm)) {
                    isMaterialPureDynamic = false;
                }
            }

            // 正向硬拦截：动模板不能由静素材承载
            if (needDynamic && isMaterialStatic && "VIDEO".equalsIgnoreCase(profile.material().getMaterialType())
                    && materialMovementKnown) {
                return new SegmentEvaluation(-1.0, unknownTagPenaltyCount);
            }
            // 反向硬拦截：静模板不能由纯动态素材承载（防廉价手持晃动污染氛围段）
            if (TAG_STATIC.equals(segment.cameraMovementTag())
                    && "VIDEO".equalsIgnoreCase(profile.material().getMaterialType())
                    && hasAnyHighlight
                    && materialMovementKnown
                    && isMaterialPureDynamic) {
                return new SegmentEvaluation(-1.0, unknownTagPenaltyCount);
            }
            if (TAG_UNKNOWN.equals(segment.cameraMovementTag()) || !materialMovementKnown) {
                score -= UNKNOWN_TAG_PENALTY;
                unknownTagPenaltyCount++;
            }
        }

        String matAcousticEnv = profile.semanticTagsNode().path("acousticEnvironment").asText("")
                .toLowerCase(Locale.ROOT);
        if ("asmr".equals(templateAcousticEnv)
                && ("noisy".equals(matAcousticEnv) || "speech_focused".equals(matAcousticEnv))) {
            score -= 0.4;
        }

        // ── 镜头类型匹配 ──
        // MG 模板无视频素材时跳过镜头类型匹配
        if (!mgSkipVideoChecks) {
            if (!TAG_UNKNOWN.equals(segment.shotTypeTag())) {
                boolean shotTypeMatch = false;
                boolean shotTypeKnown = false;
                for (TemplateMatchCanonicalizer.CanonicalHighlight highlight : profile.highlights()) {
                    String st = highlight.shotTypeTag();
                    if (!TAG_UNKNOWN.equals(st)) {
                        shotTypeKnown = true;
                    }
                    if (segment.shotTypeTag().equals(st)) {
                        shotTypeMatch = true;
                        break;
                    }
                }
                if (!shotTypeMatch) {
                    score -= 0.2;
                }
                if (!shotTypeKnown) {
                    score -= UNKNOWN_TAG_PENALTY;
                    unknownTagPenaltyCount++;
                }
            } else {
                score -= UNKNOWN_TAG_PENALTY;
                unknownTagPenaltyCount++;
            }
        }

        boolean hasRole = false;
        JsonNode rolesNode = profile.semanticTagsNode().path("suitableRoles");
        if (rolesNode.isArray()) {
            for (JsonNode r : rolesNode) {
                if (r.asText().equalsIgnoreCase(segment.role())) {
                    hasRole = true;
                    break;
                }
            }
        }
        if (!hasRole) {
            score -= 0.3;
        }

        double visualFunctionPenalty = calculateVisualFunctionPenalty(profile, segment);
        score -= visualFunctionPenalty;

        double matDur = profile.material().getDuration() != null ? profile.material().getDuration() : 0.0;
        if (matDur > 0 && matDur < segment.minDuration()) {
            double diff = segment.minDuration() - matDur;
            score -= (diff * 0.05);
        }

        Integer matWidth = profile.material().getWidth();
        Integer matHeight = profile.material().getHeight();
        if (matWidth != null && matHeight != null) {
            double matRatio = (double) matWidth / matHeight;
            double tplRatio = 9.0 / 16.0;
            if ("16:9".equals(templateAspectRatio)) {
                tplRatio = 16.0 / 9.0;
            } else if ("1:1".equals(templateAspectRatio)) {
                tplRatio = 1.0;
            }
            if (Math.abs(matRatio - tplRatio) > 0.1) {
                score -= 0.05;
            }
        }

        String lightVibe = profile.semanticTagsNode().path("lightVibe").asText("");
        if (lightVibe.contains("dark") || lightVibe.contains("overexposed")) {
            score -= 0.05;
        }

        return new SegmentEvaluation(Math.max(0.0, score), unknownTagPenaltyCount);
    }

    private double calculateVisualFunctionPenalty(
            TemplateMatchCanonicalizer.CanonicalMaterial profile,
            TemplateMatchCanonicalizer.CanonicalSegment segment) {
        List<String> requiredFunctions = segment.requiredVisualFunctions();
        if (requiredFunctions == null || requiredFunctions.isEmpty()) {
            return 0.0;
        }
        Set<String> materialFunctions = inferMaterialVisualFunctions(profile);
        if (materialFunctions.isEmpty()) {
            return 0.1;
        }
        int matched = 0;
        int expected = 0;
        for (String function : requiredFunctions) {
            if (function == null || function.isBlank()) {
                continue;
            }
            String normalized = function.trim().toLowerCase(Locale.ROOT);
            if (!isSupportedVisualFunction(normalized)) {
                continue;
            }
            expected++;
            if (materialFunctions.contains(normalized)) {
                matched++;
            }
        }
        if (expected == 0) {
            return 0.0;
        }
        double coverage = (double) matched / expected;
        return (1.0 - coverage) * 0.15;
    }

    private Set<String> inferMaterialVisualFunctions(TemplateMatchCanonicalizer.CanonicalMaterial profile) {
        Set<String> functions = new HashSet<>();
        JsonNode rolesNode = profile.semanticTagsNode().path("suitableRoles");
        if (rolesNode.isArray()) {
            for (JsonNode node : rolesNode) {
                String role = node.asText("").trim().toLowerCase(Locale.ROOT);
                if ("hook".equals(role)) {
                    functions.add("subject_intro");
                    functions.add("emotion_push");
                } else if ("body".equals(role)) {
                    functions.add("usage_process");
                    functions.add("detail_showcase");
                } else if ("climax".equals(role)) {
                    functions.add("emotion_push");
                    functions.add("proof_or_comparison");
                } else if ("outro".equals(role)) {
                    functions.add("benefit_recall");
                    functions.add("cta_prompt");
                    functions.add("static_summary_card");
                }
            }
        }

        for (TemplateMatchCanonicalizer.CanonicalHighlight highlight : profile.highlights()) {
            String actionState = safeLower(highlight.actionState());
            String textType = safeLower(highlight.textType());
            String shotTypeTag = safeUpper(highlight.shotTypeTag());
            String cameraMovementTag = safeUpper(highlight.cameraMovementTag());

            if ("CLOSE_UP".equals(shotTypeTag)) {
                functions.add("detail_showcase");
            }
            if ("STATIC".equals(cameraMovementTag)
                    && ("title_overlay".equals(textType) || "normal_subtitle".equals(textType))) {
                functions.add("static_summary_card");
            }
            if ("title_overlay".equals(textType)) {
                functions.add("cta_prompt");
                functions.add("benefit_recall");
            }
            if (actionState.contains("showcase") || actionState.contains("display") || actionState.contains("detail")
                    || actionState.contains("closeup") || actionState.contains("product")) {
                functions.add("detail_showcase");
            }
            if (actionState.contains("process") || actionState.contains("usage") || actionState.contains("demo")
                    || actionState.contains("operation") || actionState.contains("apply")
                    || actionState.contains("assemble")
                    || actionState.contains("cook") || actionState.contains("step")) {
                functions.add("usage_process");
            }
            if (actionState.contains("compare") || actionState.contains("before_after")
                    || actionState.contains("proof") || actionState.contains("test")
                    || actionState.contains("review")) {
                functions.add("proof_or_comparison");
            }
            if (actionState.contains("intro") || actionState.contains("hook") || actionState.contains("arrival")) {
                functions.add("subject_intro");
            }
            if (actionState.contains("cta") || actionState.contains("prompt") || actionState.contains("summary")
                    || actionState.contains("recall") || actionState.contains("benefit")) {
                functions.add("cta_prompt");
                functions.add("benefit_recall");
            }
        }
        return functions;
    }

    private boolean isSupportedVisualFunction(String normalized) {
        return "subject_intro".equals(normalized)
                || "detail_showcase".equals(normalized)
                || "usage_process".equals(normalized)
                || "emotion_push".equals(normalized)
                || "proof_or_comparison".equals(normalized)
                || "benefit_recall".equals(normalized)
                || "cta_prompt".equals(normalized)
                || "static_summary_card".equals(normalized);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record SegmentEvaluation(double score, int unknownTagPenaltyCount) {
    }
}
