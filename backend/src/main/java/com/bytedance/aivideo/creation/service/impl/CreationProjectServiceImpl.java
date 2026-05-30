package com.bytedance.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.creation.entity.CreationProjectEntity;
import com.bytedance.aivideo.creation.mapper.CreationProjectMapper;
import com.bytedance.aivideo.creation.service.CreationProjectService;
import com.bytedance.aivideo.creation.service.CreationTemplateSnapshotService;
import com.bytedance.aivideo.deconstruct.entity.DeconstructTemplateEntity;
import com.bytedance.aivideo.deconstruct.entity.ProjectTemplateSnapshotEntity;
import com.bytedance.aivideo.deconstruct.service.DeconstructTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 创作项目服务实现。
 */
@Service
public class CreationProjectServiceImpl extends ServiceImpl<CreationProjectMapper, CreationProjectEntity> implements CreationProjectService {

    private static final String PROJECT_STATUS_DRAFT = "DRAFT";

    private final DeconstructTemplateService deconstructTemplateService;
    private final CreationTemplateSnapshotService creationTemplateSnapshotService;

    public CreationProjectServiceImpl(
            DeconstructTemplateService deconstructTemplateService,
            CreationTemplateSnapshotService creationTemplateSnapshotService
    ) {
        this.deconstructTemplateService = deconstructTemplateService;
        this.creationTemplateSnapshotService = creationTemplateSnapshotService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreationProjectEntity createProject(String title, String description) {
        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "title 不能为空");
        }
        CreationProjectEntity entity = new CreationProjectEntity();
        entity.setProjectId("crp_" + UUID.randomUUID().toString().replace("-", ""));
        entity.setTitle(title.trim());
        entity.setDescription(description == null ? null : description.trim());
        entity.setStatus(PROJECT_STATUS_DRAFT);
        entity.setTemplateId(null);
        entity.setTemplateSnapshotId(null);
        entity.setTemplateSnapshotJson(null);
        this.baseMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreationProjectEntity bindTemplate(String projectId, String templateId, Integer templateVersion) {
        CreationProjectEntity project = requireActiveProject(projectId);
        DeconstructTemplateEntity template = deconstructTemplateService.getTemplateVersion(templateId, templateVersion);
        ProjectTemplateSnapshotEntity snapshot = creationTemplateSnapshotService.createOrActivate(project.getProjectId(), template);
        project.setTemplateId(snapshot.getTemplateId());
        project.setTemplateSnapshotId(snapshot.getSnapshotId());
        project.setTemplateSnapshotJson(snapshot.getSnapshotJson());
        this.baseMapper.updateById(project);
        return project;
    }

    @Override
    public CreationProjectEntity requireActiveProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "projectId 不能为空");
        }
        CreationProjectEntity project = this.baseMapper.selectOne(
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

    @Override
    public Page<CreationProjectEntity> listProjects(int page, int size, String keyword) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(size, 1);
        LambdaQueryWrapper<CreationProjectEntity> wrapper = new LambdaQueryWrapper<CreationProjectEntity>()
                .isNull(CreationProjectEntity::getDeletedAt)
                .orderByDesc(CreationProjectEntity::getUpdatedAt);
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(CreationProjectEntity::getTitle, trimmed)
                    .or()
                    .like(CreationProjectEntity::getDescription, trimmed));
        }
        return this.baseMapper.selectPage(new Page<>(normalizedPage, normalizedSize), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreationProjectEntity updateProjectBasics(String projectId, String title, String description) {
        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "title 不能为空");
        }
        CreationProjectEntity entity = requireActiveProject(projectId);
        entity.setTitle(title.trim());
        entity.setDescription(description == null ? null : description.trim());
        this.baseMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(String projectId) {
        CreationProjectEntity entity = requireActiveProject(projectId);
        this.baseMapper.deleteById(entity.getId());
    }
}
