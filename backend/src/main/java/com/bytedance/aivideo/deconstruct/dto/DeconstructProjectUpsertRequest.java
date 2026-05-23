package com.bytedance.aivideo.deconstruct.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 拆解项目新建/更新请求。
 */
@Data
public class DeconstructProjectUpsertRequest {

    @NotBlank(message = "title 不能为空")
    @Size(max = 200, message = "title 长度不能超过200")
    private String title;

    @Size(max = 1000, message = "description 长度不能超过1000")
    private String description;

    private List<String> tags;

    @Size(max = 1024, message = "coverUrl 长度不能超过1024")
    private String coverUrl;
}
