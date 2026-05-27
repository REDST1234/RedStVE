package com.bytedance.aivideo.deconstruct.controller;

import com.bytedance.aivideo.common.api.ApiResponse;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.deconstruct.dto.DeconstructTemplateResponse;
import com.bytedance.aivideo.deconstruct.dto.TemplatePublishFromTaskRequest;
import com.bytedance.aivideo.deconstruct.entity.DeconstructTemplateEntity;
import com.bytedance.aivideo.deconstruct.service.DeconstructTemplateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 全局模板管理接口。
 */
@RestController
@RequestMapping("/api/v1/templates")
public class DeconstructTemplateController {

    private final DeconstructTemplateService deconstructTemplateService;

    public DeconstructTemplateController(DeconstructTemplateService deconstructTemplateService) {
        this.deconstructTemplateService = deconstructTemplateService;
    }

    @PostMapping("/publish-from-task")
    public ApiResponse<DeconstructTemplateResponse> publishFromTask(
            @Valid @RequestBody TemplatePublishFromTaskRequest request
    ) {
        DeconstructTemplateEntity entity = deconstructTemplateService.publishFromTask(
                request.getTaskId().trim(),
                request.getTemplateJson().trim()
        );
        return ApiResponse.success(toResponse(entity));
    }

    @GetMapping("/{templateId}")
    public ApiResponse<DeconstructTemplateResponse> getTemplate(
            @PathVariable("templateId") String templateId,
            @RequestParam(value = "version", required = false) Integer version
    ) {
        if (version != null && version <= 0) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "version 必须大于0");
        }
        DeconstructTemplateEntity entity = deconstructTemplateService.getTemplateVersion(templateId, version);
        return ApiResponse.success(toResponse(entity));
    }

    @GetMapping
    public ApiResponse<List<DeconstructTemplateResponse>> listTemplates(
            @RequestParam(value = "templateId", required = false) String templateId
    ) {
        List<DeconstructTemplateResponse> list = deconstructTemplateService.listTemplates(templateId).stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.success(list);
    }

    private DeconstructTemplateResponse toResponse(DeconstructTemplateEntity entity) {
        DeconstructTemplateResponse response = new DeconstructTemplateResponse();
        response.setTemplateId(entity.getTemplateId());
        response.setTemplateVersion(entity.getTemplateVersion());
        response.setTemplateName(entity.getTemplateName());
        response.setCategoryId(entity.getCategoryId());
        response.setStatus(entity.getStatus());
        response.setSourceTaskId(entity.getSourceTaskId());
        response.setSnapshotHash(entity.getSnapshotHash());
        response.setTemplateJson(entity.getTemplateJson());
        response.setCreatedAt(toUtc(entity.getCreatedAt()));
        response.setUpdatedAt(toUtc(entity.getUpdatedAt()));
        return response;
    }

    private OffsetDateTime toUtc(java.time.LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.ofHours(8)).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
