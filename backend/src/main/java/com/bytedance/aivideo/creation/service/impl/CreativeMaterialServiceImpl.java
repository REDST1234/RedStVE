package com.bytedance.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.MediaUploadProperties;
import com.bytedance.aivideo.creation.entity.CreativeMaterialEntity;
import com.bytedance.aivideo.creation.entity.CreativeMaterialGridEntity;
import com.bytedance.aivideo.creation.mapper.CreativeMaterialGridMapper;
import com.bytedance.aivideo.creation.mapper.CreativeMaterialMapper;
import com.bytedance.aivideo.creation.service.AssetProfilerService;
import com.bytedance.aivideo.creation.service.CreationProjectService;
import com.bytedance.aivideo.creation.service.CreativeMaterialService;
import com.bytedance.aivideo.engine.ffmpeg.api.MediaProbeEngine;
import com.bytedance.aivideo.engine.ffmpeg.model.MediaProbeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 创作素材服务实现。
 */
@Slf4j
@Service
public class CreativeMaterialServiceImpl extends ServiceImpl<CreativeMaterialMapper, CreativeMaterialEntity> implements CreativeMaterialService {

    private static final String MATERIAL_STATUS_UPLOADED = "UPLOADED";
    private static final String MATERIAL_STATUS_DELETED = "DELETED";
    private static final String MATERIAL_STATUS_PROFILING = "PROFILING";
    private static final String MATERIAL_STATUS_PROFILED = "PROFILED";
    private static final String MATERIAL_STATUS_FAILED = "FAILED";
    private static final String MATERIAL_TYPE_VIDEO = "VIDEO";
    private static final String MATERIAL_TYPE_IMAGE = "IMAGE";
    private static final String MATERIAL_TYPE_TEXT = "TEXT";

    private final CreationProjectService creationProjectService;
    private final AssetProfilerService assetProfilerService;
    private final CreativeMaterialGridMapper creativeMaterialGridMapper;
    private final MediaProbeEngine mediaProbeEngine;
    private final MediaUploadProperties mediaUploadProperties;
    private final StringRedisTemplate stringRedisTemplate;

    public CreativeMaterialServiceImpl(
            CreationProjectService creationProjectService,
            AssetProfilerService assetProfilerService,
            CreativeMaterialGridMapper creativeMaterialGridMapper,
            MediaProbeEngine mediaProbeEngine,
            MediaUploadProperties mediaUploadProperties,
            StringRedisTemplate stringRedisTemplate
    ) {
        this.creationProjectService = creationProjectService;
        this.assetProfilerService = assetProfilerService;
        this.creativeMaterialGridMapper = creativeMaterialGridMapper;
        this.mediaProbeEngine = mediaProbeEngine;
        this.mediaUploadProperties = mediaUploadProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreativeMaterialEntity uploadAsset(String projectId, String materialType, MultipartFile file, String textContent) {
        creationProjectService.requireActiveProject(projectId);
        String normalizedType = normalizeMaterialType(materialType);
        checkAssetLimit(projectId, normalizedType);

        CreativeMaterialEntity entity = new CreativeMaterialEntity();
        entity.setProjectId(projectId.trim());
        entity.setMaterialType(normalizedType);
        entity.setStatus(MATERIAL_STATUS_UPLOADED);

        if (MATERIAL_TYPE_TEXT.equals(normalizedType)) {
            if (textContent == null || textContent.isBlank()) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "TEXT 素材 textContent 不能为空");
            }
            entity.setTextContent(textContent.trim());
            entity.setOriginalFileName("text_asset.txt");
            entity.setFilePath(null);
            entity.setFileSize((long) textContent.trim().length());
            entity.setFormat("txt");
        } else {
            if (file == null || file.isEmpty()) {
                throw new BizException(ErrorCode.INVALID_REQUEST, normalizedType + " 素材 file 不能为空");
            }
            String originalFileName = file.getOriginalFilename() == null ? "unknown.bin" : file.getOriginalFilename();
            String extension = extractExtension(originalFileName);
            Path storedPath = storeMaterialFile(file, extension);

            entity.setOriginalFileName(originalFileName);
            entity.setFilePath(storedPath.toString());
            entity.setFileSize(file.getSize());
            entity.setFormat(extension);

            if (MATERIAL_TYPE_VIDEO.equals(normalizedType)) {
                MediaProbeResult probeResult = mediaProbeEngine.probe(storedPath);
                entity.setDuration(probeResult.getDuration());
                entity.setWidth(probeResult.getWidth());
                entity.setHeight(probeResult.getHeight());
            }
            if (MATERIAL_TYPE_IMAGE.equals(normalizedType)) {
                fillImageSize(entity, storedPath);
            }
        }

        this.baseMapper.insert(entity);
        log.info("creative asset upload persisted: projectId={}, materialType={}, materialBizId={}",
                projectId, normalizedType, entity.getBizId());

        clearRecommendationCache(projectId);

        return entity;
    }

    @Override
    public List<CreativeMaterialEntity> listProjectAssets(String projectId) {
        creationProjectService.requireActiveProject(projectId);
        return this.baseMapper.selectList(
                new LambdaQueryWrapper<CreativeMaterialEntity>()
                        .eq(CreativeMaterialEntity::getProjectId, projectId.trim())
                        .ne(CreativeMaterialEntity::getStatus, MATERIAL_STATUS_DELETED)
                        .isNull(CreativeMaterialEntity::getDeletedAt)
                        .orderByDesc(CreativeMaterialEntity::getCreatedAt)
        );
    }

    @Override
    public int confirmAssetsForProfiling(String projectId, String materialType) {
        creationProjectService.requireActiveProject(projectId);
        
        LambdaQueryWrapper<CreativeMaterialEntity> wrapper = new LambdaQueryWrapper<CreativeMaterialEntity>()
                .eq(CreativeMaterialEntity::getProjectId, projectId.trim())
                .ne(CreativeMaterialEntity::getStatus, MATERIAL_STATUS_DELETED)
                .isNull(CreativeMaterialEntity::getDeletedAt)
                .orderByAsc(CreativeMaterialEntity::getCreatedAt);
                
        if (materialType != null && !materialType.isBlank()) {
            wrapper.eq(CreativeMaterialEntity::getMaterialType, materialType.trim());
        }
        
        List<CreativeMaterialEntity> assets = this.baseMapper.selectList(wrapper);

        if (assets.isEmpty()) {
            throw new BizException(ErrorCode.MATERIAL_NOT_FOUND, "项目无可用素材，请先上传");
        }

        int triggered = 0;
        for (CreativeMaterialEntity asset : assets) {
            String status = asset.getStatus() == null ? "" : asset.getStatus().trim().toUpperCase(Locale.ROOT);
            // 支持重复提取：UPLOADED/FAILED/PROFILED 都可以重跑覆盖。
            // PROFILING 中的素材不重复触发，避免并发多次执行。
            if (MATERIAL_STATUS_PROFILING.equals(status)) {
                continue;
            }
            if (MATERIAL_STATUS_UPLOADED.equals(status)
                    || MATERIAL_STATUS_FAILED.equals(status)
                    || MATERIAL_STATUS_PROFILED.equals(status)) {
                asset.setStatus(MATERIAL_STATUS_PROFILING);
                this.baseMapper.updateById(asset);
                assetProfilerService.profileAsset(String.valueOf(asset.getBizId()));
                triggered++;
            }
        }
        return triggered;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProjectAsset(String projectId, String materialBizId) {
        if (materialBizId == null || materialBizId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "materialBizId 不能为空");
        }
        Long materialBizIdLong = parseMaterialBizId(materialBizId);
        creationProjectService.requireActiveProject(projectId);

        CreativeMaterialEntity material = this.baseMapper.selectOne(
                new LambdaQueryWrapper<CreativeMaterialEntity>()
                        .eq(CreativeMaterialEntity::getProjectId, projectId.trim())
                        .eq(CreativeMaterialEntity::getBizId, materialBizIdLong)
                        .isNull(CreativeMaterialEntity::getDeletedAt)
                        .last("LIMIT 1")
        );
        if (material == null) {
            throw new BizException(ErrorCode.MATERIAL_NOT_FOUND, "素材不存在或已删除: " + materialBizId);
        }

        this.baseMapper.update(
                null,
                new LambdaUpdateWrapper<CreativeMaterialEntity>()
                        .eq(CreativeMaterialEntity::getId, material.getId())
                        .eq(CreativeMaterialEntity::getProjectId, projectId.trim())
                        .isNull(CreativeMaterialEntity::getDeletedAt)
                        .set(CreativeMaterialEntity::getStatus, MATERIAL_STATUS_DELETED)
        );
        this.baseMapper.delete(
                new LambdaQueryWrapper<CreativeMaterialEntity>()
                        .eq(CreativeMaterialEntity::getId, material.getId())
                        .eq(CreativeMaterialEntity::getProjectId, projectId.trim())
                        .isNull(CreativeMaterialEntity::getDeletedAt)
        );

        deletePhysicalAssetFiles(material);
        log.info("creative asset deleted: projectId={}, materialBizId={}", projectId, materialBizIdLong);
        
        clearRecommendationCache(projectId);
    }

    private void clearRecommendationCache(String projectId) {
        if (projectId != null && !projectId.isBlank()) {
            String pId = projectId.trim();
            // 缓存 key 包含 topN 后缀，需用模式匹配删除所有 topN 变体
            var templateKeys = stringRedisTemplate.keys("aivideo:recommend:template:" + pId + ":*");
            if (templateKeys != null && !templateKeys.isEmpty()) {
                stringRedisTemplate.delete(templateKeys);
            }
            var bgmKeys = stringRedisTemplate.keys("aivideo:recommend:bgm:" + pId + ":*");
            if (bgmKeys != null && !bgmKeys.isEmpty()) {
                stringRedisTemplate.delete(bgmKeys);
            }
            log.info("Cleared recommendation cache for project: {}", pId);
        }
    }

    private String normalizeMaterialType(String materialType) {
        if (materialType == null || materialType.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "materialType 不能为空");
        }
        String normalized = materialType.trim().toUpperCase(Locale.ROOT);
        if (!MATERIAL_TYPE_VIDEO.equals(normalized)
                && !MATERIAL_TYPE_IMAGE.equals(normalized)
                && !MATERIAL_TYPE_TEXT.equals(normalized)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "不支持的 materialType: " + materialType);
        }
        return normalized;
    }

    private void checkAssetLimit(String projectId, String materialType) {
        long count = this.baseMapper.selectCount(
                new LambdaQueryWrapper<CreativeMaterialEntity>()
                        .eq(CreativeMaterialEntity::getProjectId, projectId)
                        .eq(CreativeMaterialEntity::getMaterialType, materialType)
                        .ne(CreativeMaterialEntity::getStatus, MATERIAL_STATUS_DELETED)
                        .isNull(CreativeMaterialEntity::getDeletedAt)
        );

        if (MATERIAL_TYPE_VIDEO.equals(materialType) && count >= 1) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "当前项目最多只能上传 1 个视频素材");
        }
        if (MATERIAL_TYPE_IMAGE.equals(materialType) && count >= 5) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "当前项目最多只能上传 5 张图片素材");
        }
        if (MATERIAL_TYPE_TEXT.equals(materialType) && count >= 10) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "当前项目最多只能上传 10 段文本/卖点信息");
        }
    }

    private Path storeMaterialFile(MultipartFile file, String extension) {
        Path root = resolveCreationMaterialRoot();
        try {
            Files.createDirectories(root);
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
            Path target = root.resolve(filename).normalize();
            file.transferTo(target);
            return target;
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "保存创作素材失败: " + ex.getMessage());
        }
    }

    private void fillImageSize(CreativeMaterialEntity entity, Path imagePath) {
        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image != null) {
                entity.setWidth(image.getWidth());
                entity.setHeight(image.getHeight());
            }
        } catch (Exception ex) {
            log.warn("read image size failed: path={}, reason={}", imagePath, ex.getMessage());
        }
    }

    private Path resolveCreationMaterialRoot() {
        Path uploadRoot = Paths.get(mediaUploadProperties.getDirectory()).toAbsolutePath();
        Path storageRoot = uploadRoot.getParent();
        if (storageRoot == null) {
            storageRoot = Paths.get("storage").toAbsolutePath();
        }
        return storageRoot.resolve("creation-material");
    }

    private String extractExtension(String originalFileName) {
        int index = originalFileName.lastIndexOf('.');
        if (index < 0 || index == originalFileName.length() - 1) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "上传文件缺少后缀名");
        }
        return originalFileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private void deletePhysicalAssetFiles(CreativeMaterialEntity material) {
        if (material.getFilePath() == null || material.getFilePath().isBlank()) {
            return;
        }
        Path sourcePath = Paths.get(material.getFilePath()).toAbsolutePath().normalize();
        try {
            List<Path> gridFiles = new ArrayList<>();
            List<CreativeMaterialGridEntity> gridRows = creativeMaterialGridMapper.selectList(
                    new LambdaQueryWrapper<CreativeMaterialGridEntity>()
                            .eq(CreativeMaterialGridEntity::getMaterialBizId, material.getBizId())
                            .isNull(CreativeMaterialGridEntity::getDeletedAt)
            );
            for (CreativeMaterialGridEntity row : gridRows) {
                if (row.getFilePath() != null && !row.getFilePath().isBlank()) {
                    gridFiles.add(Paths.get(row.getFilePath()).toAbsolutePath().normalize());
                }
            }

            Files.deleteIfExists(sourcePath);
            Path parent = sourcePath.getParent();
            if (parent != null) {
                String bizId = String.valueOf(material.getBizId());
                Files.deleteIfExists(parent.resolve("audio").resolve(bizId + ".mp3"));
                // 兜底删除旧单图命名
                Files.deleteIfExists(parent.resolve("grids").resolve(bizId + "_grid.jpg"));
            }

            for (Path gridFile : gridFiles) {
                Files.deleteIfExists(gridFile);
            }

            creativeMaterialGridMapper.delete(new LambdaQueryWrapper<CreativeMaterialGridEntity>()
                    .eq(CreativeMaterialGridEntity::getMaterialBizId, material.getBizId())
                    .isNull(CreativeMaterialGridEntity::getDeletedAt));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.MATERIAL_DELETE_FAILED, "删除素材文件失败: " + ex.getMessage());
        }
    }

    private Long parseMaterialBizId(String materialBizId) {
        try {
            long value = Long.parseLong(materialBizId.trim());
            if (value <= 0) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "materialBizId 不合法");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "materialBizId 不合法");
        }
    }
}
