package com.bytedance.aivideo.deconstruct.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 拆解项目素材项响应。
 */
@Data
public class DeconstructProjectMaterialItemResponse {

    /**
     * 素材业务主键，使用字符串避免前端 JS 精度丢失。
     */
    private String materialBizId;

    private String originalFileName;

    private String taskId;

    private String filePath;

    private String coverCandidate;

    private Double duration;

    private Integer width;

    private Integer height;

    private String format;

    private OffsetDateTime createdAt;
}
