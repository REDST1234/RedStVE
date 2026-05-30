package com.bytedance.aivideo.creation.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 上传创作素材响应。
 */
@Data
public class UploadCreativeAssetResponse {

    private String projectId;
    private String materialBizId;
    private String materialType;
    private String originalFileName;
    private String status;
    private String filePath;
    private Long fileSize;
    private OffsetDateTime createdAt;
}
