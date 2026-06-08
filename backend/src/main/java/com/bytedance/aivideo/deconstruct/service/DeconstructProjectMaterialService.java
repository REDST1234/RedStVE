package com.bytedance.aivideo.deconstruct.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.deconstruct.dto.DeconstructProjectMaterialItemResponse;
import com.bytedance.aivideo.deconstruct.entity.DeconstructProjectEntity;
import com.bytedance.aivideo.deconstruct.entity.DeconstructProjectMaterialEntity;
import com.bytedance.aivideo.deconstruct.mapper.DeconstructProjectMapper;
import com.bytedance.aivideo.deconstruct.mapper.DeconstructProjectMaterialMapper;
import com.bytedance.aivideo.video.entity.AnalysisVideoMaterialEntity;
import com.bytedance.aivideo.video.mapper.AnalysisVideoMaterialMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 拆解项目与素材关联服务。
 */
@Service
public class DeconstructProjectMaterialService {

    private static final String RELATION_TYPE_PRIMARY = "PRIMARY";
    private static final String MATERIAL_STATUS_ACTIVE = "ACTIVE";

    private final DeconstructProjectMapper deconstructProjectMapper;
    private final DeconstructProjectMaterialMapper deconstructProjectMaterialMapper;
    private final AnalysisVideoMaterialMapper analysisVideoMaterialMapper;

    public DeconstructProjectMaterialService(
            DeconstructProjectMapper deconstructProjectMapper,
            DeconstructProjectMaterialMapper deconstructProjectMaterialMapper,
            AnalysisVideoMaterialMapper analysisVideoMaterialMapper
    ) {
        this.deconstructProjectMapper = deconstructProjectMapper;
        this.deconstructProjectMaterialMapper = deconstructProjectMaterialMapper;
        this.analysisVideoMaterialMapper = analysisVideoMaterialMapper;
    }

    public void validateProjectExists(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "projectId 不能为空");
        }
        long count = deconstructProjectMapper.selectCount(
                new LambdaQueryWrapper<DeconstructProjectEntity>()
                        .eq(DeconstructProjectEntity::getProjectId, projectId.trim())
        );
        if (count <= 0) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在: " + projectId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindUploadedMaterial(String projectId, Long materialBizId, String taskId) {
        validateProjectExists(projectId);
        if (materialBizId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "materialBizId 不能为空");
        }

        String normalizedProjectId = projectId.trim();
        DeconstructProjectMaterialEntity relation = deconstructProjectMaterialMapper.selectOne(
                new LambdaQueryWrapper<DeconstructProjectMaterialEntity>()
                        .eq(DeconstructProjectMaterialEntity::getProjectId, normalizedProjectId)
                        .eq(DeconstructProjectMaterialEntity::getMaterialBizId, materialBizId)
        );
        if (relation == null) {
            relation = new DeconstructProjectMaterialEntity();
            relation.setProjectId(normalizedProjectId);
            relation.setMaterialBizId(materialBizId);
            relation.setTaskId(taskId);
            relation.setRelationType(RELATION_TYPE_PRIMARY);
            relation.setSortOrder(0);
            deconstructProjectMaterialMapper.insert(relation);
            return;
        }

        relation.setTaskId(taskId);
        if (relation.getRelationType() == null || relation.getRelationType().isBlank()) {
            relation.setRelationType(RELATION_TYPE_PRIMARY);
        }
        if (relation.getSortOrder() == null) {
            relation.setSortOrder(0);
        }
        deconstructProjectMaterialMapper.updateById(relation);
    }

    public List<DeconstructProjectMaterialItemResponse> listProjectMaterials(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return List.of();
        }
        List<DeconstructProjectMaterialEntity> relations = deconstructProjectMaterialMapper.selectList(
                new LambdaQueryWrapper<DeconstructProjectMaterialEntity>()
                        .eq(DeconstructProjectMaterialEntity::getProjectId, projectId.trim())
                        .isNull(DeconstructProjectMaterialEntity::getDeletedAt)
                        .orderByAsc(DeconstructProjectMaterialEntity::getSortOrder)
                        .orderByDesc(DeconstructProjectMaterialEntity::getCreatedAt)
        );
        if (relations.isEmpty()) {
            return List.of();
        }

        List<Long> materialBizIds = relations.stream()
                .map(DeconstructProjectMaterialEntity::getMaterialBizId)
                .filter(id -> id != null && id > 0)
                .toList();
        if (materialBizIds.isEmpty()) {
            return List.of();
        }

        List<AnalysisVideoMaterialEntity> materials = analysisVideoMaterialMapper.selectList(
                new LambdaQueryWrapper<AnalysisVideoMaterialEntity>()
                        .in(AnalysisVideoMaterialEntity::getBizId, materialBizIds)
                        .eq(AnalysisVideoMaterialEntity::getStatus, MATERIAL_STATUS_ACTIVE)
                        .isNull(AnalysisVideoMaterialEntity::getDeletedAt)
        );
        Map<Long, AnalysisVideoMaterialEntity> materialMap = materials.stream()
                .collect(Collectors.toMap(AnalysisVideoMaterialEntity::getBizId, Function.identity(), (a, b) -> a));

        List<DeconstructProjectMaterialItemResponse> result = new ArrayList<>();
        for (DeconstructProjectMaterialEntity relation : relations) {
            AnalysisVideoMaterialEntity material = materialMap.get(relation.getMaterialBizId());
            if (material == null) {
                continue;
            }
            DeconstructProjectMaterialItemResponse item = new DeconstructProjectMaterialItemResponse();
            item.setMaterialBizId(relation.getMaterialBizId() == null ? null : String.valueOf(relation.getMaterialBizId()));
            item.setTaskId(relation.getTaskId());
            item.setOriginalFileName(material.getOriginalFileName());
            item.setFilePath(material.getFilePath());
            item.setCoverCandidate(resolveCoverCandidate(material.getFilePath()));
            item.setDuration(material.getDuration() == null ? null : material.getDuration().doubleValue());
            item.setWidth(material.getWidth());
            item.setHeight(material.getHeight());
            item.setFormat(material.getFormat());
            item.setCreatedAt(material.getCreatedAt() == null
                    ? null
                    : material.getCreatedAt().atOffset(ZoneOffset.ofHours(8)).withOffsetSameInstant(ZoneOffset.UTC));
            result.add(item);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeAllRelationsByMaterialBizId(Long materialBizId) {
        if (materialBizId == null || materialBizId <= 0) {
            return;
        }
        deconstructProjectMaterialMapper.delete(
                new LambdaQueryWrapper<DeconstructProjectMaterialEntity>()
                        .eq(DeconstructProjectMaterialEntity::getMaterialBizId, materialBizId)
        );
    }

    private String resolveCoverCandidate(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        
        // 视频源文件不能作为图片封面，直接返回 null 以便触发前端的 fallback 封面
        if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi") || lower.endsWith(".mkv")) {
            return null;
        }

        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file://")) {
            return normalized;
        }
        if (normalized.matches("^[a-zA-Z]:[\\\\/].+")) {
            return "file:///" + normalized.replace("\\", "/");
        }
        if (normalized.startsWith("/")) {
            return "file://" + normalized;
        }
        return "file:///" + normalized.replace("\\", "/");
    }
}
