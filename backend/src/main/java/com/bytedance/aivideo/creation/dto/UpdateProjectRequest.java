package com.bytedance.aivideo.creation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新创作项目基础信息请求。
 */
@Data
public class UpdateProjectRequest {

    @NotBlank(message = "title 不能为空")
    private String title;

    private String description;

    private String aspectRatio;
}
