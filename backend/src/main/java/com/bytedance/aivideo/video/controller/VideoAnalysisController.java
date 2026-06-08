package com.bytedance.aivideo.video.controller;

import com.bytedance.aivideo.common.api.ApiResponse;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.video.dto.VideoBatchUploadResponse;
import com.bytedance.aivideo.video.dto.VideoTaskResultResponse;
import com.bytedance.aivideo.video.dto.VideoUploadResponse;
import com.bytedance.aivideo.video.dto.timeline.TimelineMatchResult;
import com.bytedance.aivideo.video.service.AsrAnalysisService;
import com.bytedance.aivideo.video.service.KeyFrameAnalysisService;
import com.bytedance.aivideo.video.service.SceneAnalysisService;
import com.bytedance.aivideo.video.service.TimelineMatcherService;
import com.bytedance.aivideo.video.service.VideoAnalysisResultService;
import com.bytedance.aivideo.video.service.VideoAnalysisRetryService;
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
    private final SceneAnalysisService sceneAnalysisService;
    private final KeyFrameAnalysisService keyFrameAnalysisService;
    private final TimelineMatcherService timelineMatcherService;
    private final VideoAnalysisResultService videoAnalysisResultService;
    private final VideoAnalysisRetryService videoAnalysisRetryService;
    private final VideoMaterialService videoMaterialService;
    private final com.bytedance.aivideo.video.service.StructureAnalyzerService structureAnalyzerService;

    public VideoAnalysisController(
            VideoUploadService videoUploadService,
            AsrAnalysisService asrAnalysisService,
            SceneAnalysisService sceneAnalysisService,
            KeyFrameAnalysisService keyFrameAnalysisService,
            TimelineMatcherService timelineMatcherService,
            VideoAnalysisResultService videoAnalysisResultService,
            VideoAnalysisRetryService videoAnalysisRetryService,
            VideoMaterialService videoMaterialService,
            com.bytedance.aivideo.video.service.StructureAnalyzerService structureAnalyzerService
    ) {
        this.videoUploadService = videoUploadService;
        this.asrAnalysisService = asrAnalysisService;
        this.sceneAnalysisService = sceneAnalysisService;
        this.keyFrameAnalysisService = keyFrameAnalysisService;
        this.timelineMatcherService = timelineMatcherService;
        this.videoAnalysisResultService = videoAnalysisResultService;
        this.videoAnalysisRetryService = videoAnalysisRetryService;
        this.videoMaterialService = videoMaterialService;
        this.structureAnalyzerService = structureAnalyzerService;
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
     * Debug 模式手动触发 镜头拆分 异步分析。
     */
    @PostMapping("/tasks/{taskId}/debug/scene-trigger")
    public ApiResponse<Boolean> triggerSceneDebug(@PathVariable("taskId") String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        sceneAnalysisService.runSceneDetectAsync(taskId);
        return ApiResponse.success(Boolean.TRUE);
    }

    /**
     * Debug 模式手动触发 关键帧抽取 异步分析。
     */
    @PostMapping("/tasks/{taskId}/debug/keyframe-trigger")
    public ApiResponse<Boolean> triggerKeyframeDebug(@PathVariable("taskId") String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        keyFrameAnalysisService.runExtractAsync(taskId);
        return ApiResponse.success(Boolean.TRUE);
    }

    /**
     * 正式触发拆解链路：仅在用户点击“开始提取视频”后调用。
     */
    @PostMapping("/tasks/{taskId}/start-extraction")
    public ApiResponse<Boolean> startExtraction(@PathVariable("taskId") String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        return ApiResponse.success(videoUploadService.startExtraction(taskId));
    }

    /**
     * 从当前失败阶段开始重试，不重复执行已成功阶段。
     */
    @PostMapping("/tasks/{taskId}/retry")
    public ApiResponse<Boolean> retryFromFailedStage(@PathVariable("taskId") String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        videoAnalysisRetryService.retryFromFailedStage(taskId);
        return ApiResponse.success(Boolean.TRUE);
    }

    /**
     * 重新拆解模版（仅重置并执行 LLM 阶段）。
     */
    @PostMapping("/tasks/{taskId}/retry-llm")
    public ApiResponse<Boolean> retryLlmOnly(@PathVariable("taskId") String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        videoAnalysisRetryService.retryLlmOnly(taskId);
        return ApiResponse.success(Boolean.TRUE);
    }

    /**
     * Debug 模式手动触发 TimelineMatcher（多路时间轴归一）。
     */
    @PostMapping("/tasks/{taskId}/debug/timeline-match")
    public ApiResponse<TimelineMatchResult> triggerTimelineMatchDebug(
            @PathVariable("taskId") String taskId,
            @RequestParam(value = "threshold", defaultValue = "0.15") double threshold) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        TimelineMatchResult result = timelineMatcherService.match(taskId, threshold);
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);
            videoAnalysisResultService.saveTimelineDebug(taskId, json);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to save debug timeline");
        }
        return ApiResponse.success(result);
    }

    /**
     * Debug 模式手动触发 大一统提纯及视频结构分析。
     */
    @PostMapping("/tasks/{taskId}/debug/llm-analysis")
    public ApiResponse<com.bytedance.aivideo.video.service.StructureAnalyzerService.AnalysisOutput> triggerLlmAnalysisDebug(
            @PathVariable("taskId") String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        return ApiResponse.success(structureAnalyzerService.triggerLlmAnalysis(taskId));
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
     * 读取底层原始的 scene_result.json 镜头切分数据
     */
    @GetMapping("/tasks/{taskId}/raw-scene")
    public ApiResponse<Object> getRawSceneResult(@PathVariable("taskId") String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId 不能为空");
        }
        return ApiResponse.success(videoAnalysisResultService.getRawSceneResult(taskId));
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
