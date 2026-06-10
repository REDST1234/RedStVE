package com.bytedance.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.creation.dto.ProjectBgmBindingResponse;
import com.bytedance.aivideo.creation.dto.ProjectBgmSelectRequest;
import com.bytedance.aivideo.creation.entity.CreationProjectBgmBindingEntity;
import com.bytedance.aivideo.creation.entity.CreationProjectEntity;
import com.bytedance.aivideo.creation.mapper.CreationProjectBgmBindingMapper;
import com.bytedance.aivideo.creation.mapper.CreationProjectMapper;
import com.bytedance.aivideo.creation.service.CreationProjectBgmBindingService;
import com.bytedance.aivideo.creation.util.BgmMixLevelResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
public class CreationProjectBgmBindingServiceImpl extends ServiceImpl<CreationProjectBgmBindingMapper, CreationProjectBgmBindingEntity>
        implements CreationProjectBgmBindingService {

    private static final String STATUS_SELECTED = "SELECTED";
    private static final String STATUS_REPLACED = "REPLACED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STORAGE_DIR_NAME = "storage";

    private final CreationProjectMapper creationProjectMapper;
    private final ObjectMapper objectMapper;

    public CreationProjectBgmBindingServiceImpl(
            CreationProjectMapper creationProjectMapper,
            ObjectMapper objectMapper
    ) {
        this.creationProjectMapper = creationProjectMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectBgmBindingResponse selectBgm(String projectId, ProjectBgmSelectRequest request) {
        requireActiveProject(projectId);
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "BGM 选择参数不能为空");
        }
        if (request.getAudioId() == null || request.getAudioId().isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "audioId 不能为空");
        }
        if (request.getFilePath() == null || request.getFilePath().isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "filePath 不能为空");
        }

        CreationProjectBgmBindingEntity current = findCurrentBindingEntity(projectId);
        if (current != null) {
            current.setStatus(STATUS_REPLACED);
            this.baseMapper.updateById(current);
        }

        String mixLevel = BgmMixLevelResolver.normalize(request.getMixLevel());
        CreationProjectBgmBindingEntity entity = new CreationProjectBgmBindingEntity();
        entity.setProjectId(projectId);
        entity.setAudioId(request.getAudioId().trim());
        entity.setAudioName(trimToNull(request.getAudioName()));
        entity.setPreviewUrl(request.getFilePath().trim());
        entity.setSrcPath(resolveRenderableSrc(request.getFilePath().trim()));
        entity.setSourceType(trimToNull(request.getSourceType()) == null ? "RECOMMENDED" : request.getSourceType().trim());
        entity.setRecommendScore(request.getRecommendScore());
        entity.setSemanticScore(request.getSemanticScore());
        entity.setEnergyCurveScore(request.getEnergyCurveScore());
        entity.setDurationBpmScore(request.getDurationBpmScore());
        entity.setMixLevel(mixLevel);
        entity.setVolume(BgmMixLevelResolver.resolveVolume(mixLevel));
        entity.setLoopEnabled(request.getLoopEnabled() == null ? Boolean.TRUE : request.getLoopEnabled());
        entity.setFadeInFrames(request.getFadeInFrames() == null ? 15 : request.getFadeInFrames());
        entity.setFadeOutFrames(request.getFadeOutFrames() == null ? 30 : request.getFadeOutFrames());
        entity.setDuckingEnabled(request.getDuckingEnabled() == null ? Boolean.TRUE : request.getDuckingEnabled());
        entity.setDuckingRatio(request.getDuckingRatio());
        entity.setMetadataJson(writeMetadataJson(request.getMetadata()));
        entity.setStatus(STATUS_SELECTED);
        this.baseMapper.insert(entity);
        return toResponse(entity);
    }

    @Override
    public ProjectBgmBindingResponse getCurrentBgm(String projectId) {
        requireActiveProject(projectId);
        CreationProjectBgmBindingEntity entity = findCurrentBindingEntity(projectId);
        return entity == null ? null : toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCurrentBgm(String projectId) {
        requireActiveProject(projectId);
        CreationProjectBgmBindingEntity current = findCurrentBindingEntity(projectId);
        if (current != null) {
            current.setStatus(STATUS_DISABLED);
            this.baseMapper.updateById(current);
        }
    }

    @Override
    public CreationProjectBgmBindingEntity findCurrentBindingEntity(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        return this.baseMapper.selectOne(
                new LambdaQueryWrapper<CreationProjectBgmBindingEntity>()
                        .eq(CreationProjectBgmBindingEntity::getProjectId, projectId.trim())
                        .eq(CreationProjectBgmBindingEntity::getStatus, STATUS_SELECTED)
                        .isNull(CreationProjectBgmBindingEntity::getDeletedAt)
                        .orderByDesc(CreationProjectBgmBindingEntity::getUpdatedAt)
                        .last("LIMIT 1")
        );
    }

    private CreationProjectEntity requireActiveProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "projectId 不能为空");
        }
        CreationProjectEntity project = creationProjectMapper.selectOne(
                new LambdaQueryWrapper<CreationProjectEntity>()
                        .eq(CreationProjectEntity::getProjectId, projectId.trim())
                        .isNull(CreationProjectEntity::getDeletedAt)
                        .last("LIMIT 1")
        );
        if (project == null) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND, "创作项目不存在: " + projectId);
        }
        return project;
    }

    private String resolveRenderableSrc(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        String normalized = filePath.replace("\\", "/");
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        
        // 抹平可能带有的旧绝对路径，转化为统一的 storage/... 相对形式
        int storageIdx = normalized.indexOf("/storage/");
        if (storageIdx == -1 && normalized.startsWith("storage/")) {
            normalized = "/" + normalized;
            storageIdx = 0;
        }
        
        if (storageIdx != -1) {
            String relativePart = normalized.substring(storageIdx + "/storage/".length());
            return "storage/" + relativePart;
        }
        
        return filePath;
    }

    private String writeMetadataJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "metadata 序列化失败");
        }
    }

    private Map<String, Object> readMetadataJson(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private ProjectBgmBindingResponse toResponse(CreationProjectBgmBindingEntity entity) {
        ProjectBgmBindingResponse response = new ProjectBgmBindingResponse();
        response.setProjectId(entity.getProjectId());
        response.setVersionId(entity.getVersionId());
        response.setAudioId(entity.getAudioId());
        response.setAudioName(entity.getAudioName());
        response.setSrcPath(entity.getSrcPath());
        response.setPreviewUrl(entity.getPreviewUrl());
        response.setSourceType(entity.getSourceType());
        response.setRecommendScore(entity.getRecommendScore());
        response.setSemanticScore(entity.getSemanticScore());
        response.setEnergyCurveScore(entity.getEnergyCurveScore());
        response.setDurationBpmScore(entity.getDurationBpmScore());
        response.setMixLevel(entity.getMixLevel());
        response.setVolume(entity.getVolume());
        response.setLoopEnabled(entity.getLoopEnabled());
        response.setFadeInFrames(entity.getFadeInFrames());
        response.setFadeOutFrames(entity.getFadeOutFrames());
        response.setDuckingEnabled(entity.getDuckingEnabled());
        response.setDuckingRatio(entity.getDuckingRatio());
        response.setMetadata(readMetadataJson(entity.getMetadataJson()));
        response.setStatus(entity.getStatus());
        response.setCreatedAt(toUtc(entity.getCreatedAt()));
        response.setUpdatedAt(toUtc(entity.getUpdatedAt()));
        return response;
    }

    private OffsetDateTime toUtc(java.time.LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.ofHours(8)).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
