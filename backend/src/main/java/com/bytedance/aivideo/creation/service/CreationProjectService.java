package com.bytedance.aivideo.creation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bytedance.aivideo.creation.entity.CreationProjectEntity;

public interface CreationProjectService extends IService<CreationProjectEntity> {
    
    // 初始化创作项目
    CreationProjectEntity createProject(String title, String description);
    
    // 绑定项目与解构模板
    void bindTemplate(String projectId, String templateId);
}
