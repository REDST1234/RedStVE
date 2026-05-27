package com.bytedance.aivideo.deconstruct.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.deconstruct.dto.TemplateSnapshotDto;
import com.bytedance.aivideo.deconstruct.entity.DeconstructTemplateEntity;
import com.bytedance.aivideo.deconstruct.entity.ProjectTemplateSnapshotEntity;
import com.bytedance.aivideo.deconstruct.mapper.ProjectTemplateSnapshotMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 项目模板快照服务。
 */
@Service
public class ProjectTemplateSnapshotService {

    private static final String SNAPSHOT_STATUS_ACTIVE = "ACTIVE";
    private static final String SNAPSHOT_STATUS_REPLACED = "REPLACED";

    private final ProjectTemplateSnapshotMapper projectTemplateSnapshotMapper;
    private final DeconstructProjectService deconstructProjectService;
    private final DeconstructTemplateService deconstructTemplateService;

    public ProjectTemplateSnapshotService(
            ProjectTemplateSnapshotMapper projectTemplateSnapshotMapper,
            DeconstructProjectService deconstructProjectService,
            DeconstructTemplateService deconstructTemplateService
    ) {
        this.projectTemplateSnapshotMapper = projectTemplateSnapshotMapper;
        this.deconstructProjectService = deconstructProjectService;
        this.deconstructTemplateService = deconstructTemplateService;
    }

    @Transactional(rollbackFor = Exception.class)
    public TemplateSnapshotDto createSnapshot(String templateId, String projectId, Integer templateVersion) {
        if (projectId == null || projectId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "projectId 不能为空");
        }
        deconstructProjectService.ensureProjectExists(projectId.trim());
        DeconstructTemplateEntity template = deconstructTemplateService.getTemplateVersion(templateId, templateVersion);

        ProjectTemplateSnapshotEntity exists = projectTemplateSnapshotMapper.selectOne(
                new LambdaQueryWrapper<ProjectTemplateSnapshotEntity>()
                        .eq(ProjectTemplateSnapshotEntity::getProjectId, projectId.trim())
                        .eq(ProjectTemplateSnapshotEntity::getTemplateId, template.getTemplateId())
                        .eq(ProjectTemplateSnapshotEntity::getTemplateVersion, template.getTemplateVersion())
                        .eq(ProjectTemplateSnapshotEntity::getSnapshotHash, template.getSnapshotHash())
                        .isNull(ProjectTemplateSnapshotEntity::getDeletedAt)
                        .orderByDesc(ProjectTemplateSnapshotEntity::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (exists != null) {
            activateSnapshot(projectId.trim(), exists.getSnapshotId());
            return toDto(projectTemplateSnapshotMapper.selectById(exists.getId()));
        }

        ProjectTemplateSnapshotEntity entity = new ProjectTemplateSnapshotEntity();
        entity.setSnapshotId("snap_" + UUID.randomUUID().toString().replace("-", ""));
        entity.setProjectId(projectId.trim());
        entity.setTemplateId(template.getTemplateId());
        entity.setTemplateVersion(template.getTemplateVersion());
        entity.setStatus(SNAPSHOT_STATUS_ACTIVE);
        entity.setSourceTaskId(template.getSourceTaskId());
        entity.setSnapshotJson(template.getTemplateJson());
        entity.setSnapshotHash(template.getSnapshotHash());
        projectTemplateSnapshotMapper.insert(entity);

        activateSnapshot(projectId.trim(), entity.getSnapshotId());
        return toDto(entity);
    }

    public List<TemplateSnapshotDto> listProjectSnapshots(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "projectId 不能为空");
        }
        deconstructProjectService.ensureProjectExists(projectId.trim());
        return projectTemplateSnapshotMapper.selectList(
                        new LambdaQueryWrapper<ProjectTemplateSnapshotEntity>()
                                .eq(ProjectTemplateSnapshotEntity::getProjectId, projectId.trim())
                                .isNull(ProjectTemplateSnapshotEntity::getDeletedAt)
                                .orderByDesc(ProjectTemplateSnapshotEntity::getCreatedAt)
                ).stream()
                .map(this::toDto)
                .toList();
    }

    public ProjectTemplateSnapshotEntity resolveSnapshot(String projectId, String snapshotId) {
        if (projectId == null || projectId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "projectId 不能为空");
        }
        deconstructProjectService.ensureProjectExists(projectId.trim());

        LambdaQueryWrapper<ProjectTemplateSnapshotEntity> wrapper = new LambdaQueryWrapper<ProjectTemplateSnapshotEntity>()
                .eq(ProjectTemplateSnapshotEntity::getProjectId, projectId.trim())
                .isNull(ProjectTemplateSnapshotEntity::getDeletedAt);
        if (snapshotId != null && !snapshotId.isBlank()) {
            wrapper.eq(ProjectTemplateSnapshotEntity::getSnapshotId, snapshotId.trim());
        } else {
            wrapper.eq(ProjectTemplateSnapshotEntity::getStatus, SNAPSHOT_STATUS_ACTIVE)
                    .orderByDesc(ProjectTemplateSnapshotEntity::getUpdatedAt)
                    .last("LIMIT 1");
        }

        ProjectTemplateSnapshotEntity entity = projectTemplateSnapshotMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BizException(ErrorCode.SNAPSHOT_NOT_FOUND, "模板快照不存在: " + projectId);
        }
        return entity;
    }

    private void activateSnapshot(String projectId, String activeSnapshotId) {
        List<ProjectTemplateSnapshotEntity> snapshots = projectTemplateSnapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectTemplateSnapshotEntity>()
                        .eq(ProjectTemplateSnapshotEntity::getProjectId, projectId)
                        .isNull(ProjectTemplateSnapshotEntity::getDeletedAt)
        );
        for (ProjectTemplateSnapshotEntity item : snapshots) {
            String status = activeSnapshotId.equals(item.getSnapshotId()) ? SNAPSHOT_STATUS_ACTIVE : SNAPSHOT_STATUS_REPLACED;
            if (!status.equals(item.getStatus())) {
                item.setStatus(status);
                projectTemplateSnapshotMapper.updateById(item);
            }
        }
    }

    private TemplateSnapshotDto toDto(ProjectTemplateSnapshotEntity entity) {
        TemplateSnapshotDto dto = new TemplateSnapshotDto();
        dto.setSnapshotId(entity.getSnapshotId());
        dto.setProjectId(entity.getProjectId());
        dto.setTemplateId(entity.getTemplateId());
        dto.setTemplateVersion(entity.getTemplateVersion());
        dto.setStatus(entity.getStatus());
        dto.setSnapshotHash(entity.getSnapshotHash());
        dto.setSourceTaskId(entity.getSourceTaskId());
        dto.setCreatedAt(toUtc(entity.getCreatedAt()));
        dto.setUpdatedAt(toUtc(entity.getUpdatedAt()));
        return dto;
    }

    private OffsetDateTime toUtc(java.time.LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.ofHours(8)).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
