package com.bytedance.aivideo.creation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bytedance.aivideo.creation.dto.ProjectBgmBindingResponse;
import com.bytedance.aivideo.creation.dto.ProjectBgmSelectRequest;
import com.bytedance.aivideo.creation.entity.CreationProjectBgmBindingEntity;

public interface CreationProjectBgmBindingService extends IService<CreationProjectBgmBindingEntity> {

    ProjectBgmBindingResponse selectBgm(String projectId, ProjectBgmSelectRequest request);

    ProjectBgmBindingResponse getCurrentBgm(String projectId);

    void clearCurrentBgm(String projectId);

    CreationProjectBgmBindingEntity findCurrentBindingEntity(String projectId);
}
