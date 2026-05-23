package com.bytedance.aivideo.video.dto;

import lombok.Data;

/**
 * ffprobe 媒体探测输出。
 */
@Data
public class MediaInfo {
    private Double duration;
    private Integer width;
    private Integer height;
    private Double fps;
    private String codec;
    private Long bitrate;
    private Boolean hasAudio;
    private String audioCodec;
    private String format;
}
