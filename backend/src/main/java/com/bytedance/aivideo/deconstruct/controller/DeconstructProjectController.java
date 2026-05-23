package com.bytedance.aivideo.deconstruct.controller;

import com.bytedance.aivideo.common.api.ApiResponse;
import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.deconstruct.dto.DeconstructProjectListResponse;
import com.bytedance.aivideo.deconstruct.dto.DeconstructProjectResponse;
import com.bytedance.aivideo.deconstruct.dto.DeconstructProjectUpsertRequest;
import com.bytedance.aivideo.deconstruct.service.DeconstructProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 拆解项目联调接口。
 */
@RestController
@RequestMapping("/api/v1/deconstruct/projects")
public class DeconstructProjectController {

    private final DeconstructProjectService deconstructProjectService;

    public DeconstructProjectController(DeconstructProjectService deconstructProjectService) {
        this.deconstructProjectService = deconstructProjectService;
    }

    @PostMapping
    public ApiResponse<DeconstructProjectResponse> createProject(
            @Valid @RequestBody DeconstructProjectUpsertRequest request
    ) {
        return ApiResponse.success(deconstructProjectService.createProject(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<DeconstructProjectResponse> updateProject(
            @PathVariable("id") String id,
            @Valid @RequestBody DeconstructProjectUpsertRequest request
    ) {
        validateProjectId(id);
        return ApiResponse.success(deconstructProjectService.updateProject(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deleteProject(@PathVariable("id") String id) {
        validateProjectId(id);
        return ApiResponse.success(deconstructProjectService.deleteProject(id));
    }

    @GetMapping("/{id}")
    public ApiResponse<DeconstructProjectResponse> getProject(@PathVariable("id") String id) {
        validateProjectId(id);
        return ApiResponse.success(deconstructProjectService.getProject(id));
    }

    @GetMapping
    public ApiResponse<DeconstructProjectListResponse> listProjects(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return ApiResponse.success(deconstructProjectService.listProjects(page, size, keyword));
    }

    private void validateProjectId(String id) {
        if (id == null || id.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "id 不能为空");
        }
    }
}
