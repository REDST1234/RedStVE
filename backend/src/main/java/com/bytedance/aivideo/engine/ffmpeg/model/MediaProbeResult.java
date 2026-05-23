package com.bytedance.aivideo.engine.ffmpeg.model;

import lombok.Data;

/**
 * 媒体探测结果模型。
 */
@Data
public class MediaProbeResult {

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
