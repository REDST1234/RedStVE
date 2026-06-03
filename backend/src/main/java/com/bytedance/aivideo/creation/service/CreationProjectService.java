package com.bytedance.aivideo.creation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bytedance.aivideo.creation.entity.CreationProjectEntity;

public interface CreationProjectService extends IService<CreationProjectEntity> {
    
    // 初始化创作项目
    CreationProjectEntity createProject(String title, String description);
    
    // 绑定项目与解构模板
    CreationProjectEntity bindTemplate(String projectId, String templateId, Integer templateVersion);

    // 获取项目并校验存在
    CreationProjectEntity requireActiveProject(String projectId);

    // 分页查询项目列表
    Page<CreationProjectEntity> listProjects(int page, int size, String keyword);

    // 更新项目基础信息
    CreationProjectEntity updateProjectBasics(String projectId, String title, String description);

    // 删除项目（逻辑删除）
    void deleteProject(String projectId);

    // 智能编排并调用 Remotion 服务生成视频
    void generateVideo(String projectId, String aspectRatio);

    // 强制重新编排并生成视频（清除缓存，不复用上次脚本）
    void regenerateVideo(String projectId, String aspectRatio);

    // 查询视频渲染进度 (结合 Redis 与 MySQL)
    com.bytedance.aivideo.engine.remotion.dto.RenderResponse getRenderStatus(String projectId);
}
