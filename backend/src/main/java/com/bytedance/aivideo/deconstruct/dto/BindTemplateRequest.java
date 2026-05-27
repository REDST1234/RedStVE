package com.bytedance.aivideo.deconstruct.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 项目绑定模板请求。
 */
@Data
public class BindTemplateRequest {

    @NotBlank(message = "templateId 不能为空")
    private String templateId;

    @Positive(message = "templateVersion 必须大于0")
    private Integer templateVersion;
}
