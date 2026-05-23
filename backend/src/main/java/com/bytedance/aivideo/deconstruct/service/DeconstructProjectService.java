package com.bytedance.aivideo.deconstruct.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.deconstruct.dto.DeconstructProjectListResponse;
import com.bytedance.aivideo.deconstruct.dto.DeconstructProjectMaterialItemResponse;
import com.bytedance.aivideo.deconstruct.dto.DeconstructProjectResponse;
import com.bytedance.aivideo.deconstruct.dto.DeconstructProjectUpsertRequest;
import com.bytedance.aivideo.deconstruct.entity.DeconstructProjectEntity;
import com.bytedance.aivideo.deconstruct.mapper.DeconstructProjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 拆解项目 CRUD 服务。
 */
@Service
public class DeconstructProjectService {

    private static final String DEFAULT_STATUS = "PENDING";
    private static final Pattern WINDOWS_PATH_PATTERN = Pattern.compile("^[a-zA-Z]:[\\\\/].+");

    private final DeconstructProjectMapper deconstructProjectMapper;
    private final DeconstructProjectMaterialService deconstructProjectMaterialService;
    private final ObjectMapper objectMapper;

    public DeconstructProjectService(
            DeconstructProjectMapper deconstructProjectMapper,
            DeconstructProjectMaterialService deconstructProjectMaterialService,
            ObjectMapper objectMapper
    ) {
        this.deconstructProjectMapper = deconstructProjectMapper;
        this.deconstructProjectMaterialService = deconstructProjectMaterialService;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public DeconstructProjectResponse createProject(DeconstructProjectUpsertRequest request) {
        DeconstructProjectEntity entity = new DeconstructProjectEntity();
        entity.setProjectId("prj_" + IdWorker.getIdStr());
        entity.setTitle(request.getTitle().trim());
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setTagsJson(toTagsJson(request.getTags()));
        entity.setCoverUrl(trimToNull(request.getCoverUrl()));
        entity.setStatus(DEFAULT_STATUS);
        deconstructProjectMapper.insert(entity);
        return toResponse(entity, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeconstructProjectResponse updateProject(String projectId, DeconstructProjectUpsertRequest request) {
        DeconstructProjectEntity entity = getByProjectId(projectId);
        entity.setTitle(request.getTitle().trim());
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setTagsJson(toTagsJson(request.getTags()));
        entity.setCoverUrl(trimToNull(request.getCoverUrl()));
        deconstructProjectMapper.updateById(entity);
        return toResponse(entity, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteProject(String projectId) {
        int deleted = deconstructProjectMapper.delete(
                new LambdaQueryWrapper<DeconstructProjectEntity>()
                        .eq(DeconstructProjectEntity::getProjectId, projectId)
        );
        if (deleted <= 0) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在: " + projectId);
        }
        return true;
    }

    public DeconstructProjectResponse getProject(String projectId) {
        return toResponse(getByProjectId(projectId), true);
    }

    public DeconstructProjectListResponse listProjects(int page, int size, String keyword) {
        int pageNo = Math.max(1, page);
        int pageSize = Math.max(1, Math.min(100, size));

        LambdaQueryWrapper<DeconstructProjectEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String trimmedKeyword = keyword.trim();
            wrapper.and(q -> q.like(DeconstructProjectEntity::getTitle, trimmedKeyword)
                    .or()
                    .like(DeconstructProjectEntity::getDescription, trimmedKeyword));
        }
        wrapper.orderByDesc(DeconstructProjectEntity::getUpdatedAt);

        IPage<DeconstructProjectEntity> paged = deconstructProjectMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        DeconstructProjectListResponse response = new DeconstructProjectListResponse();
        response.setTotal(paged.getTotal());
        response.setPage(pageNo);
        response.setSize(pageSize);
        response.setList(paged.getRecords().stream().map(entity -> toResponse(entity, false)).toList());
        return response;
    }

    private DeconstructProjectEntity getByProjectId(String projectId) {
        DeconstructProjectEntity entity = deconstructProjectMapper.selectOne(
                new LambdaQueryWrapper<DeconstructProjectEntity>()
                        .eq(DeconstructProjectEntity::getProjectId, projectId)
        );
        if (entity == null) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在: " + projectId);
        }
        return entity;
    }

    private DeconstructProjectResponse toResponse(DeconstructProjectEntity entity, boolean includeMaterials) {
        DeconstructProjectResponse response = new DeconstructProjectResponse();
        response.setId(entity.getProjectId());
        response.setTitle(entity.getTitle());
        response.setDescription(entity.getDescription());
        response.setTags(parseTags(entity.getTagsJson()));
        response.setStatus(entity.getStatus());

        List<DeconstructProjectMaterialItemResponse> materials = includeMaterials
                ? deconstructProjectMaterialService.listProjectMaterials(entity.getProjectId())
                : List.of();
        response.setMaterials(includeMaterials ? materials : null);

        String coverUrl = resolveCoverUrl(entity.getCoverUrl());
        if ((coverUrl == null || coverUrl.isBlank()) && includeMaterials) {
            coverUrl = materials.stream()
                    .map(DeconstructProjectMaterialItemResponse::getCoverCandidate)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        response.setCoverUrl(coverUrl);
        response.setCreatedAt(toUtcOffset(entity.getCreatedAt()));
        response.setUpdatedAt(toUtcOffset(entity.getUpdatedAt()));
        return response;
    }

    private OffsetDateTime toUtcOffset(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.ofHours(8)).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private String toTagsJson(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        List<String> normalized = tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .toList();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "tags 序列化失败: " + ex.getMessage());
        }
    }

    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "tags 解析失败: " + ex.getMessage());
        }
    }

    private String resolveCoverUrl(String coverUrl) {
        if (coverUrl == null || coverUrl.isBlank()) {
            return null;
        }
        String normalized = coverUrl.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file://")) {
            return normalized;
        }
        if (WINDOWS_PATH_PATTERN.matcher(normalized).matches()) {
            String unixStyle = normalized.replace("\\", "/");
            return "file:///" + unixStyle;
        }

        String contextBase;
        try {
            contextBase = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        } catch (IllegalStateException ex) {
            return normalized;
        }

        if (normalized.startsWith("/")) {
            return contextBase + normalized;
        }

        String relative = normalized.startsWith("./") ? normalized.substring(2) : normalized;
        return contextBase + "/" + relative;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
