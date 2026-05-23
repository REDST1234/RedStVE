package com.bytedance.aivideo.video.dto;

import lombok.Data;

/**
 * 批量上传单文件结果。
 */
@Data
public class VideoUploadItemResponse {
    /**
     * 素材业务主键，使用字符串避免前端 JS 精度丢失。
     */
    private String materialBizId;
    private String taskId;
    private String originalFileName;
    private MediaInfo mediaInfo;
}
