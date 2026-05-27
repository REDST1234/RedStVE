package com.bytedance.aivideo.deconstruct.controller;

import com.bytedance.aivideo.common.api.ApiResponse;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.deconstruct.dto.BindTemplateRequest;
import com.bytedance.aivideo.deconstruct.dto.ScriptwriterRunRequest;
import com.bytedance.aivideo.deconstruct.dto.ScriptwriterRunResponse;
import com.bytedance.aivideo.deconstruct.dto.TemplateSnapshotCreateRequest;
import com.bytedance.aivideo.deconstruct.dto.TemplateSnapshotDto;
import com.bytedance.aivideo.deconstruct.service.ProjectTemplateSnapshotService;
import com.bytedance.aivideo.deconstruct.service.ScriptwriterOrchestratorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模板快照与编剧运行接口。
 */
@RestController
public class TemplateSnapshotController {

    private final ProjectTemplateSnapshotService projectTemplateSnapshotService;
    private final ScriptwriterOrchestratorService scriptwriterOrchestratorService;

    public TemplateSnapshotController(
            ProjectTemplateSnapshotService projectTemplateSnapshotService,
            ScriptwriterOrchestratorService scriptwriterOrchestratorService
    ) {
        this.projectTemplateSnapshotService = projectTemplateSnapshotService;
        this.scriptwriterOrchestratorService = scriptwriterOrchestratorService;
    }

    @PostMapping("/api/v1/templates/{templateId}/snapshots")
    public ApiResponse<TemplateSnapshotDto> createTemplateSnapshot(
            @PathVariable("templateId") String templateId,
            @Valid @RequestBody TemplateSnapshotCreateRequest request
    ) {
        validateId(templateId, "templateId 不能为空");
        return ApiResponse.success(projectTemplateSnapshotService.createSnapshot(
                templateId.trim(),
                request.getProjectId(),
                request.getTemplateVersion()
        ));
    }

    @GetMapping("/api/v1/projects/{projectId}/template-snapshots")
    public ApiResponse<List<TemplateSnapshotDto>> listProjectTemplateSnapshots(
            @PathVariable("projectId") String projectId
    ) {
        validateId(projectId, "projectId 不能为空");
        return ApiResponse.success(projectTemplateSnapshotService.listProjectSnapshots(projectId.trim()));
    }

    @PostMapping("/api/v1/projects/{projectId}/bind-template")
    public ApiResponse<TemplateSnapshotDto> bindTemplateToProject(
            @PathVariable("projectId") String projectId,
            @Valid @RequestBody BindTemplateRequest request
    ) {
        validateId(projectId, "projectId 不能为空");
        validateId(request.getTemplateId(), "templateId 不能为空");
        return ApiResponse.success(projectTemplateSnapshotService.createSnapshot(
                request.getTemplateId().trim(),
                projectId.trim(),
                request.getTemplateVersion()
        ));
    }

    @PostMapping("/api/v1/projects/{projectId}/scriptwriter-run")
    public ApiResponse<ScriptwriterRunResponse> runScriptwriter(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) ScriptwriterRunRequest request
    ) {
        validateId(projectId, "projectId 不能为空");
        ScriptwriterRunRequest actualRequest = request == null ? new ScriptwriterRunRequest() : request;
        return ApiResponse.success(scriptwriterOrchestratorService.run(projectId.trim(), actualRequest));
    }

    @GetMapping("/api/v1/projects/{projectId}/scriptwriter-result")
    public ApiResponse<ScriptwriterRunResponse> getScriptwriterRunResult(
            @PathVariable("projectId") String projectId,
            @RequestParam("runId") String runId
    ) {
        validateId(projectId, "projectId 不能为空");
        validateId(runId, "runId 不能为空");
        return ApiResponse.success(scriptwriterOrchestratorService.getRunResult(projectId.trim(), runId.trim()));
    }

    private void validateId(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, message);
        }
    }
}
