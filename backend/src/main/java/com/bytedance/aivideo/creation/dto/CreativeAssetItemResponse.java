package com.bytedance.aivideo.creation.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 创作素材列表项。
 */
@Data
public class CreativeAssetItemResponse {

    private String materialBizId;
    private String materialType;
    private String originalFileName;
    private String status;
    private String filePath;
    private Long fileSize;
    private Double duration;
    private Integer width;
    private Integer height;
    private String format;
    private String textContent; // 🌟 存储文案原文
    private String profileJson; // 🌟 新增 profileJson 字段，用于前端展示
    private List<CreativeAssetGridItemResponse> gridPages;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
