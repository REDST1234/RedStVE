package com.bytedance.aivideo.creation.service;

import com.bytedance.aivideo.creation.entity.CreativeMaterialEntity;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CreativeMaterialService extends IService<CreativeMaterialEntity> {
    // 上传并落库素材
    CreativeMaterialEntity uploadAsset(String projectId, String materialType, MultipartFile file, String textContent);

    // 查询项目素材
    List<CreativeMaterialEntity> listProjectAssets(String projectId);

    /**
     * 确认并提取项目下的资产特征（仅对状态为 UPLOADED 或失败重试的资产生效）
     * @param projectId
     * @param materialType 可选，指定只确认某种类型的素材 (VIDEO, IMAGE, TEXT)
     * @return 触发分析的任务数量
     */
    int confirmAssetsForProfiling(String projectId, String materialType);

    // 删除项目素材：逻辑删除 + 磁盘物理删除
    void deleteProjectAsset(String projectId, String materialBizId);
}
