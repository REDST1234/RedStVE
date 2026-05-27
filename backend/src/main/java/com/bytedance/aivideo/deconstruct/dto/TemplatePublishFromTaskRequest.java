package com.bytedance.aivideo.deconstruct.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 从任务发布模板请求。
 */
@Data
public class TemplatePublishFromTaskRequest {

    @NotBlank(message = "taskId 不能为空")
    private String taskId;

    @NotBlank(message = "templateJson 不能为空")
    private String templateJson;
}
