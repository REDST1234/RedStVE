package com.bytedance.aivideo.video.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量上传响应。
 */
@Data
public class VideoBatchUploadResponse {
    private List<String> taskIds = new ArrayList<>();
    private int fileCount;
    private String status;
    private Integer estimatedDuration;
    private List<MediaInfo> mediaInfos = new ArrayList<>();
    private List<VideoUploadItemResponse> items = new ArrayList<>();
}
