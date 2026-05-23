package com.bytedance.aivideo.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.deconstruct.service.DeconstructProjectMaterialService;
import com.bytedance.aivideo.video.entity.AnalysisVideoMaterialEntity;
import com.bytedance.aivideo.video.mapper.AnalysisVideoMaterialMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * 拆解素材删除服务：
 * 1) 逻辑删除素材
 * 2) 清理项目关联
 * 3) 物理删除磁盘文件
 */
@Service
@Slf4j
public class VideoMaterialService {

    private static final String MATERIAL_STATUS_ACTIVE = "ACTIVE";
    private static final String MATERIAL_STATUS_DELETED = "DELETED";

    private final AnalysisVideoMaterialMapper analysisVideoMaterialMapper;
    private final DeconstructProjectMaterialService deconstructProjectMaterialService;

    public VideoMaterialService(
            AnalysisVideoMaterialMapper analysisVideoMaterialMapper,
            DeconstructProjectMaterialService deconstructProjectMaterialService
    ) {
        this.analysisVideoMaterialMapper = analysisVideoMaterialMapper;
        this.deconstructProjectMaterialService = deconstructProjectMaterialService;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMaterial(Long materialBizId) {
        if (materialBizId == null || materialBizId <= 0) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "materialBizId 不合法");
        }

        AnalysisVideoMaterialEntity entity = analysisVideoMaterialMapper.selectOne(
                new LambdaQueryWrapper<AnalysisVideoMaterialEntity>()
                        .eq(AnalysisVideoMaterialEntity::getBizId, materialBizId)
                        .eq(AnalysisVideoMaterialEntity::getStatus, MATERIAL_STATUS_ACTIVE)
                        .isNull(AnalysisVideoMaterialEntity::getDeletedAt)
        );
        if (entity == null) {
            throw new BizException(ErrorCode.MATERIAL_NOT_FOUND, "素材不存在或已删除: " + materialBizId);
        }

        log.info("material delete start: materialBizId={}, filePath={}", materialBizId, entity.getFilePath());
        entity.setStatus(MATERIAL_STATUS_DELETED);
        entity.setDeletedAt(LocalDateTime.now());
        analysisVideoMaterialMapper.updateById(entity);
        deconstructProjectMaterialService.removeAllRelationsByMaterialBizId(materialBizId);
        deletePhysicalFile(entity.getFilePath(), materialBizId);
        log.info("material delete finished: materialBizId={}", materialBizId);
        return true;
    }

    private void deletePhysicalFile(String filePath, Long materialBizId) {
        if (filePath == null || filePath.isBlank()) {
            throw new BizException(ErrorCode.MATERIAL_DELETE_FAILED, "素材路径为空，无法删除文件");
        }
        try {
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.error("material physical delete failed: materialBizId={}, filePath={}, reason={}",
                    materialBizId, filePath, ex.getMessage(), ex);
            throw new BizException(ErrorCode.MATERIAL_DELETE_FAILED, "磁盘文件删除失败: " + ex.getMessage());
        }
    }
}
