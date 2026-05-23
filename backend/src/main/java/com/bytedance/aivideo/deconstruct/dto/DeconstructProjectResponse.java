package com.bytedance.aivideo.deconstruct.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 拆解项目响应体。
 */
@Data
public class DeconstructProjectResponse {

    private String id;

    private String title;

    private String description;

    private List<String> tags;

    private String coverUrl;

    private String status;

    private List<DeconstructProjectMaterialItemResponse> materials;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
