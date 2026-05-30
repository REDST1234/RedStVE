package com.bytedance.aivideo.creation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.deconstruct.entity.DeconstructTemplateEntity;
import com.bytedance.aivideo.deconstruct.entity.ProjectTemplateSnapshotEntity;
import com.bytedance.aivideo.deconstruct.mapper.ProjectTemplateSnapshotMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CreationTemplateSnapshotService {

    private static final String SNAPSHOT_STATUS_ACTIVE = "ACTIVE";
    private static final String SNAPSHOT_STATUS_REPLACED = "REPLACED";

    private final ProjectTemplateSnapshotMapper projectTemplateSnapshotMapper;

    public CreationTemplateSnapshotService(ProjectTemplateSnapshotMapper projectTemplateSnapshotMapper) {
        this.projectTemplateSnapshotMapper = projectTemplateSnapshotMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectTemplateSnapshotEntity createOrActivate(String projectId, DeconstructTemplateEntity template) {
        ProjectTemplateSnapshotEntity exists = projectTemplateSnapshotMapper.selectOne(
                new LambdaQueryWrapper<ProjectTemplateSnapshotEntity>()
                        .eq(ProjectTemplateSnapshotEntity::getProjectId, projectId)
                        .eq(ProjectTemplateSnapshotEntity::getTemplateId, template.getTemplateId())
                        .eq(ProjectTemplateSnapshotEntity::getTemplateVersion, template.getTemplateVersion())
                        .eq(ProjectTemplateSnapshotEntity::getSnapshotHash, template.getSnapshotHash())
                        .isNull(ProjectTemplateSnapshotEntity::getDeletedAt)
                        .orderByDesc(ProjectTemplateSnapshotEntity::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (exists != null) {
            activateSnapshot(projectId, exists.getSnapshotId());
            return projectTemplateSnapshotMapper.selectById(exists.getId());
        }

        ProjectTemplateSnapshotEntity snapshot = new ProjectTemplateSnapshotEntity();
        snapshot.setSnapshotId("snap_" + UUID.randomUUID().toString().replace("-", ""));
        snapshot.setProjectId(projectId);
        snapshot.setTemplateId(template.getTemplateId());
        snapshot.setTemplateVersion(template.getTemplateVersion());
        snapshot.setStatus(SNAPSHOT_STATUS_ACTIVE);
        snapshot.setSourceTaskId(template.getSourceTaskId());
        snapshot.setSnapshotJson(template.getTemplateJson());
        snapshot.setSnapshotHash(template.getSnapshotHash());
        projectTemplateSnapshotMapper.insert(snapshot);
        activateSnapshot(projectId, snapshot.getSnapshotId());
        return snapshot;
    }

    private void activateSnapshot(String projectId, String activeSnapshotId) {
        List<ProjectTemplateSnapshotEntity> snapshots = projectTemplateSnapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectTemplateSnapshotEntity>()
                        .eq(ProjectTemplateSnapshotEntity::getProjectId, projectId)
                        .isNull(ProjectTemplateSnapshotEntity::getDeletedAt)
        );
        for (ProjectTemplateSnapshotEntity item : snapshots) {
            String nextStatus = activeSnapshotId.equals(item.getSnapshotId()) ? SNAPSHOT_STATUS_ACTIVE : SNAPSHOT_STATUS_REPLACED;
            if (!nextStatus.equals(item.getStatus())) {
                item.setStatus(nextStatus);
                projectTemplateSnapshotMapper.updateById(item);
            }
        }
    }
}
