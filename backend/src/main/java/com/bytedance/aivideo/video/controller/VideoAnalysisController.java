package com.bytedance.aivideo.video.controller;

import com.bytedance.aivideo.common.api.ApiResponse;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.video.dto.VideoBatchUploadResponse;
import com.bytedance.aivideo.video.dto.VideoTaskResultResponse;
import com.bytedance.aivideo.video.dto.VideoUploadResponse;
import com.bytedance.aivideo.video.service.AsrAnalysisService;
import com.bytedance.aivideo.video.service.VideoAnalysisResultService;
import com.bytedance.aivideo.video.service.VideoMaterialService;
import com.bytedance.aivideo.video.service.VideoUploadService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 视频拆解上传入口。
 */
@RestController
@RequestMapping("/api/v1/videos")
public class VideoAnalysisController {

    private final VideoUploadService videoUploadService;
    private final AsrAnalysisService asrAnalysisService;
    private final VideoAnalysisResultService videoAnalysisResultService;
    private final VideoMaterialService videoMaterialService;

    public VideoAnalysisController(
            VideoUploadService videoUploadService,
            AsrAnalysisService asrAnalysisService,
            VideoAnalysisResultService videoAnalysisResultService,
            VideoMaterialService videoMaterialService
    ) {
        this.videoUploadService = videoUploadService;
        this.asrAnalysisService = asrAnalysisService;
        this.videoAnalysisResultService = videoAnalysisResultService;
        this.videoMaterialService = videoMaterialService;
    }

    /**
     * 单视频上传并返回 ffprobe 基础信息。
     */
    @PostMapping("/upload")
    public ApiResponse<VideoUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "categoryHint", required = false) String categoryHint,
            @RequestParam(value = "priority", required = false) Integer priority,
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "debugMode", defaultValue = "false") boolean debugMode
    ) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "file 不能为空");
        }
        return ApiResponse.success(videoUploadService.uploadSingle(file, categoryHint, priority, projectId, debugMode));
    }

    /**
     * 批量上传（方案A：每个视频一个 taskId）。
     */
    @PostMapping("/upload/batch")
    public ApiResponse<VideoBatchUploadResponse> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "categoryHint", required = false) String categoryHint,
            @RequestParam(value = "priority", required = false) Integer priority,
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "debugMode", defaultValue = "false") boolean debugMode
    ) {
        if (files == null || files.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "files 不能为空");
        }
        return ApiResponse.success(videoUploadService.uploadBatch(files, categoryHint, priority, projectId, debugMode));
    }

    /**
     * Debug 模式手动触发 ASR 异步分析。
     */
    @PostMapping("/tasks/{taskId}/debug/asr-trigger")
    public ApiResponse<Boolean> triggerAsrDebug(@PathVariable("taskId") String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        asrAnalysisService.runAsrAsync(taskId);
        return ApiResponse.success(Boolean.TRUE);
    }

    /**
     * 查询拆解分析结果（默认不加载冷时序资产）。
     */
    @GetMapping("/tasks/{taskId}/result")
    public ApiResponse<VideoTaskResultResponse> getTaskResult(
            @PathVariable("taskId") String taskId,
            @RequestParam(value = "includeTimeline", defaultValue = "false") boolean includeTimeline
    ) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        return ApiResponse.success(videoAnalysisResultService.getTaskResult(taskId, includeTimeline));
    }

    /**
     * 删除素材（逻辑删 + 清理关联 + 物理删文件）。
     */
    @DeleteMapping("/materials/{materialBizId}")
    public ApiResponse<Boolean> deleteMaterial(@PathVariable("materialBizId") Long materialBizId) {
        if (materialBizId == null || materialBizId <= 0) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "materialBizId 不合法");
        }
        return ApiResponse.success(videoMaterialService.deleteMaterial(materialBizId));
    }
}
