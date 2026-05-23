package com.bytedance.aivideo.video.service;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.MediaUploadProperties;
import com.bytedance.aivideo.deconstruct.service.DeconstructProjectMaterialService;
import com.bytedance.aivideo.engine.ffmpeg.api.MediaProbeEngine;
import com.bytedance.aivideo.engine.ffmpeg.model.MediaProbeResult;
import com.bytedance.aivideo.video.dto.MediaInfo;
import com.bytedance.aivideo.video.dto.VideoBatchUploadResponse;
import com.bytedance.aivideo.video.dto.VideoUploadItemResponse;
import com.bytedance.aivideo.video.dto.VideoUploadResponse;
import com.bytedance.aivideo.video.entity.AnalysisVideoMaterialEntity;
import com.bytedance.aivideo.video.entity.VideoAnalysisTaskEntity;
import com.bytedance.aivideo.video.mapper.AnalysisVideoMaterialMapper;
import com.bytedance.aivideo.video.mapper.VideoAnalysisTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 上传入口业务编排：
 * 1) 校验文件
 * 2) 本地落盘
 * 3) ffprobe 探测
 * 4) 写入素材与任务记录
 */
@Service
@Slf4j
public class VideoUploadService {

    private static final String TASK_STATUS_PROCESSING = "PROCESSING";
    private static final String MATERIAL_STATUS_ACTIVE = "ACTIVE";

    private final MediaUploadProperties mediaUploadProperties;
    private final MediaProbeEngine mediaProbeEngine;
    private final AnalysisVideoMaterialMapper analysisVideoMaterialMapper;
    private final VideoAnalysisTaskMapper videoAnalysisTaskMapper;
    private final DeconstructProjectMaterialService deconstructProjectMaterialService;
    private final VideoAnalysisResultService videoAnalysisResultService;
    private final AsrAnalysisService asrAnalysisService;
    private final VideoTaskStageService videoTaskStageService;

    public VideoUploadService(
            MediaUploadProperties mediaUploadProperties,
            MediaProbeEngine mediaProbeEngine,
            AnalysisVideoMaterialMapper analysisVideoMaterialMapper,
            VideoAnalysisTaskMapper videoAnalysisTaskMapper,
            DeconstructProjectMaterialService deconstructProjectMaterialService,
            VideoAnalysisResultService videoAnalysisResultService,
            AsrAnalysisService asrAnalysisService,
            VideoTaskStageService videoTaskStageService
    ) {
        this.mediaUploadProperties = mediaUploadProperties;
        this.mediaProbeEngine = mediaProbeEngine;
        this.analysisVideoMaterialMapper = analysisVideoMaterialMapper;
        this.videoAnalysisTaskMapper = videoAnalysisTaskMapper;
        this.deconstructProjectMaterialService = deconstructProjectMaterialService;
        this.videoAnalysisResultService = videoAnalysisResultService;
        this.asrAnalysisService = asrAnalysisService;
        this.videoTaskStageService = videoTaskStageService;
    }

    @Transactional(rollbackFor = Exception.class)
    public VideoUploadResponse uploadSingle(
            MultipartFile file,
            String categoryHint,
            Integer priority,
            String projectId,
            boolean debugMode
    ) {
        log.info("video upload start: mode=single, originalFileName={}, size={}, categoryHint={}, priority={}, projectId={}, debugMode={}",
                file == null ? null : file.getOriginalFilename(),
                file == null ? null : file.getSize(),
                categoryHint,
                priority,
                projectId,
                debugMode);
        UploadResult uploadResult = handleSingleFile(file, categoryHint, priority, projectId, debugMode);

        VideoUploadResponse response = new VideoUploadResponse();
        response.setMaterialBizId(String.valueOf(uploadResult.materialBizId()));
        response.setTaskId(uploadResult.taskId());
        response.setStatus(TASK_STATUS_PROCESSING);
        response.setEstimatedDuration(uploadResult.estimatedDuration());
        response.setMediaInfo(uploadResult.mediaInfo());
        response.setOriginalFileName(uploadResult.originalFileName());
        log.info("video upload finished: mode=single, taskId={}, originalFileName={}, estimatedDurationSec={}",
                response.getTaskId(), uploadResult.originalFileName(), response.getEstimatedDuration());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public VideoBatchUploadResponse uploadBatch(
            List<MultipartFile> files,
            String categoryHint,
            Integer priority,
            String projectId,
            boolean debugMode
    ) {
        if (files == null || files.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "files 不能为空");
        }
        log.info("video upload start: mode=batch, fileCount={}, categoryHint={}, priority={}, projectId={}, debugMode={}",
                files.size(), categoryHint, priority, projectId, debugMode);

        List<UploadResult> uploadResults = new ArrayList<>();
        for (MultipartFile file : files) {
            uploadResults.add(handleSingleFile(file, categoryHint, priority, projectId, debugMode));
        }

        VideoBatchUploadResponse response = new VideoBatchUploadResponse();
        int estimatedDuration = 0;
        for (UploadResult uploadResult : uploadResults) {
            response.getTaskIds().add(uploadResult.taskId());
            response.getMediaInfos().add(uploadResult.mediaInfo());

            VideoUploadItemResponse item = new VideoUploadItemResponse();
            item.setMaterialBizId(String.valueOf(uploadResult.materialBizId()));
            item.setTaskId(uploadResult.taskId());
            item.setOriginalFileName(uploadResult.originalFileName());
            item.setMediaInfo(uploadResult.mediaInfo());
            response.getItems().add(item);

            estimatedDuration += uploadResult.estimatedDuration();
        }

        response.setFileCount(uploadResults.size());
        response.setStatus(TASK_STATUS_PROCESSING);
        response.setEstimatedDuration(estimatedDuration);
        log.info("video upload finished: mode=batch, fileCount={}, taskCount={}, estimatedDurationSec={}",
                response.getFileCount(), response.getTaskIds().size(), response.getEstimatedDuration());
        return response;
    }

    private UploadResult handleSingleFile(
            MultipartFile file,
            String categoryHint,
            Integer priority,
            String projectId,
            boolean debugMode
    ) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "上传文件不能为空");
        }
        String normalizedProjectId = normalizeProjectId(projectId);
        if (normalizedProjectId != null) {
            deconstructProjectMaterialService.validateProjectExists(normalizedProjectId);
        }

        String originalFilename = file.getOriginalFilename() == null ? "unknown.mp4" : file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        validateFile(extension, file.getSize());

        String storedFilename = buildStoredFilename(extension);
        log.info("video upload processing file: originalFileName={}, extension={}, size={}",
                originalFilename, extension, file.getSize());
        Path storedPath = storeFile(file, storedFilename);
        MediaInfo mediaInfo = toMediaInfo(mediaProbeEngine.probe(storedPath));

        long materialBizId = persistMaterial(storedPath, file.getSize(), extension, mediaInfo, originalFilename);
        String taskId = persistTask(materialBizId, storedPath, file.getSize(), priority, categoryHint);
        videoTaskStageService.initStageIfAbsent(taskId, VideoTaskStageService.STAGE_TYPE_ASR);
        videoAnalysisResultService.upsertProbeCore(materialBizId, taskId, mediaInfo);
        if (normalizedProjectId != null) {
            deconstructProjectMaterialService.bindUploadedMaterial(normalizedProjectId, materialBizId, taskId);
        }
        // 保持原有时机：仅在 ASR 线程中提取音轨。
        // Debug 模式不自动触发 ASR，由前端显式调用 debug 接口触发。
        if (!debugMode) {
            asrAnalysisService.runAsrAsync(taskId);
        }

        int estimatedDuration = mediaInfo.getDuration() == null ? 0 : (int) Math.ceil(mediaInfo.getDuration());
        log.info("video upload file completed: taskId={}, materialBizId={}, storedPath={}, projectId={}",
                taskId, materialBizId, storedPath, normalizedProjectId);
        return new UploadResult(materialBizId, taskId, originalFilename, estimatedDuration, mediaInfo);
    }

    private void validateFile(String extension, long fileSize) {
        if (extension == null) {
            throw new BizException(ErrorCode.VIDEO_FORMAT_UNSUPPORTED, "文件必须包含后缀名");
        }
        List<String> allowed = mediaUploadProperties.getAllowedExtensions();
        if (allowed != null && !allowed.isEmpty() && !allowed.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BizException(ErrorCode.VIDEO_FORMAT_UNSUPPORTED, "不支持的视频格式: " + extension);
        }
        if (fileSize > mediaUploadProperties.getMaxFileSizeBytes()) {
            throw new BizException(ErrorCode.VIDEO_TOO_LARGE, "文件大小超限: " + fileSize);
        }
    }

    private Path storeFile(MultipartFile file, String targetFilename) {
        try {
            Path rootPath = Paths.get(mediaUploadProperties.getDirectory()).toAbsolutePath();
            Files.createDirectories(rootPath);
            Path targetPath = rootPath.resolve(targetFilename).normalize();
            log.info("video file store start: targetPath={}", targetPath);
            file.transferTo(targetPath);
            log.info("video file store finished: targetPath={}", targetPath);
            return targetPath;
        } catch (IOException ex) {
            log.error("video file store failed: targetFileName={}, reason={}", targetFilename, ex.getMessage(), ex);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件保存失败: " + ex.getMessage());
        }
    }

    private long persistMaterial(Path storedPath, long fileSize, String extension, MediaInfo mediaInfo, String originalFileName) {
        AnalysisVideoMaterialEntity entity = new AnalysisVideoMaterialEntity();
        entity.setOriginalFileName(originalFileName);
        entity.setFilePath(storedPath.toString());
        entity.setFileSize(fileSize);
        entity.setDuration(mediaInfo.getDuration() == null
                ? null
                : BigDecimal.valueOf(mediaInfo.getDuration()).setScale(3, RoundingMode.HALF_UP));
        entity.setWidth(mediaInfo.getWidth());
        entity.setHeight(mediaInfo.getHeight());
        entity.setFormat(extension);
        entity.setStatus(MATERIAL_STATUS_ACTIVE);
        analysisVideoMaterialMapper.insert(entity);
        if (entity.getBizId() == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "素材 bizId 自动生成失败");
        }
        return entity.getBizId();
    }

    private String persistTask(long materialBizId, Path storedPath, long fileSize, Integer priority, String categoryHint) {
        VideoAnalysisTaskEntity taskEntity = new VideoAnalysisTaskEntity();
        taskEntity.setTaskId(UUID.randomUUID().toString());
        taskEntity.setSourceVideoBizId(materialBizId);
        taskEntity.setStatus(TASK_STATUS_PROCESSING);
        // 仅完成了上传+probe，异步分析尚未结束。
        taskEntity.setProgress(10);

        // progress_step 已不再用于并行阶段状态表达，避免被并发覆盖。
        taskEntity.setProgressStep(null);

        taskEntity.setSourceFilePath(storedPath.toString());
        taskEntity.setExtractedAudioPath(null);
        taskEntity.setFileSize(fileSize);
        taskEntity.setErrorMessage(null);
        taskEntity.setErrorStep(null);
        taskEntity.setRetryCount(0);
        taskEntity.setPriority(normalizePriority(priority));
        taskEntity.setRedisProgressKey(null);
        taskEntity.setCompletedAt(null);
        videoAnalysisTaskMapper.insert(taskEntity);
        return taskEntity.getTaskId();
    }

    private int normalizePriority(Integer priority) {
        if (priority == null) {
            return 5;
        }
        return Math.max(1, Math.min(10, priority));
    }


    private String buildStoredFilename(String extension) {
        return UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }

    private String extractExtension(String originalFilename) {
        int index = originalFilename.lastIndexOf('.');
        if (index < 0 || index == originalFilename.length() - 1) {
            return null;
        }
        return originalFilename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private MediaInfo toMediaInfo(MediaProbeResult probeResult) {
        MediaInfo mediaInfo = new MediaInfo();
        mediaInfo.setDuration(probeResult.getDuration());
        mediaInfo.setWidth(probeResult.getWidth());
        mediaInfo.setHeight(probeResult.getHeight());
        mediaInfo.setFps(probeResult.getFps());
        mediaInfo.setCodec(probeResult.getCodec());
        mediaInfo.setBitrate(probeResult.getBitrate());
        mediaInfo.setHasAudio(probeResult.getHasAudio());
        mediaInfo.setAudioCodec(probeResult.getAudioCodec());
        mediaInfo.setFormat(probeResult.getFormat());
        return mediaInfo;
    }

    private String normalizeProjectId(String projectId) {
        if (projectId == null) {
            return null;
        }
        String trimmed = projectId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record UploadResult(Long materialBizId, String taskId, String originalFileName, int estimatedDuration, MediaInfo mediaInfo) {
    }
}
