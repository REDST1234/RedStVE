package com.bytedance.aivideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频上传配置。
 */
@Component
@ConfigurationProperties(prefix = "media.upload")
public class MediaUploadProperties {

    /**
     * 上传文件落盘目录。
     */
    private String directory = "storage/analysis-video";

    /**
     * 单文件大小限制（字节）。
     */
    private long maxFileSizeBytes = 2L * 1024 * 1024 * 1024;

    /**
     * 允许上传的后缀名（小写，不含点）。
     */
    private List<String> allowedExtensions = new ArrayList<>(List.of("mp4", "mov"));

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }
}
