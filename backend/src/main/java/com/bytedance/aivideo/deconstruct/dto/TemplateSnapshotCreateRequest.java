package com.bytedance.aivideo.deconstruct.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 模板快照创建请求。
 */
@Data
public class TemplateSnapshotCreateRequest {

    @NotBlank(message = "projectId 不能为空")
    private String projectId;

    @Positive(message = "templateVersion 必须大于0")
    private Integer templateVersion;
}
