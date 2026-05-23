package com.bytedance.aivideo.video.dto;

import lombok.Data;

/**
 * 单文件上传响应。
 */
@Data
public class VideoUploadResponse {
    /**
     * 素材业务主键，使用字符串避免前端 JS 精度丢失。
     */
    private String materialBizId;
    private String taskId;
    private String originalFileName;
    private String status;
    private Integer estimatedDuration;
    private MediaInfo mediaInfo;
}
