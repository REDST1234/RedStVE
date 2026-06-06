package com.bytedance.aivideo.creation.service;

import com.bytedance.aivideo.creation.entity.CreativeMaterialEntity;

import java.util.List;

public interface TemplateStructureMatchService {

    /**
     * 计算素材集合与模板镜头结构的数学匹配度 [0, 1]
     * 若触发 Hard Veto (一票否决)，则返回 -1.0
     *
     * @param materials 用户上传的素材列表 (需为 PROFILED 状态且包含 profileJson)
     * @param templateJson 模板结构 JSON
     * @return 匹配度得分
     */
    double calculateStructureScore(List<CreativeMaterialEntity> materials, String templateJson);

    /**
     * @param categoryId 模板品类 ID（如 motion_graphics），用于品类特化评分策略
     */
    double calculateStructureScore(List<CreativeMaterialEntity> materials, String templateJson, String templateKey, String categoryId);

    default double calculateStructureScore(List<CreativeMaterialEntity> materials, String templateJson, String templateKey) {
        return calculateStructureScore(materials, templateJson, templateKey, null);
    }
}
