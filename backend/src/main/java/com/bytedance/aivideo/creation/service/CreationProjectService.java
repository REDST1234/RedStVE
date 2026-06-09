package com.bytedance.aivideo.creation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bytedance.aivideo.creation.entity.CreationProjectEntity;

public interface CreationProjectService extends IService<CreationProjectEntity> {
    
    // 初始化创作项目
    CreationProjectEntity createProject(String title, String description, String aspectRatio);
    
    // 绑定项目与解构模板
    CreationProjectEntity bindTemplate(String projectId, String templateId, Integer templateVersion);

    // 获取项目并校验存在
    CreationProjectEntity requireActiveProject(String projectId);

    // 分页查询项目列表
    Page<CreationProjectEntity> listProjects(int page, int size, String keyword);

    // 更新项目基础信息
    CreationProjectEntity updateProjectBasics(String projectId, String title, String description, String aspectRatio);

    // 删除项目（逻辑删除）
    void deleteProject(String projectId);

    // 智能编排并调用 Remotion 服务生成视频
    void generateVideo(String projectId, String aspectRatio);

    // 强制重新编排并生成视频（清除缓存，不复用上次脚本）
    void regenerateVideo(String projectId, String aspectRatio);

    // 新增：分离式流程 - 生成剧本（异步执行并返回立刻响应）
    void generateScript(String projectId, String versionStrategy, String aspectRatio);

    // 新增：分离式流程 - 提交剧本渲染
    void renderScript(String projectId, com.bytedance.aivideo.creation.dto.remotion.CompositionScript script, String aspectRatio);

    // 查询视频渲染进度 (结合 Redis 与 MySQL)
    com.bytedance.aivideo.engine.remotion.dto.RenderResponse getRenderStatus(String projectId);

    // 查询项目渲染历史记录
    java.util.List<com.bytedance.aivideo.creation.entity.RenderRecordEntity> getRenderHistory(String projectId);

    // 直接提交外部 JSON 编排脚本进行渲染（跳过 LLM 编排，仅校验+sanitize+投递）
    com.bytedance.aivideo.creation.dto.RenderFromJsonResponse renderFromJson(
            com.bytedance.aivideo.creation.dto.remotion.CompositionScript script);
}
