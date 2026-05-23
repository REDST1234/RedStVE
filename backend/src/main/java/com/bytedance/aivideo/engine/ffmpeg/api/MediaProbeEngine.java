package com.bytedance.aivideo.engine.ffmpeg.api;

import com.bytedance.aivideo.engine.ffmpeg.model.MediaProbeResult;

import java.nio.file.Path;

/**
 * 媒体探测能力接口。
 */
public interface MediaProbeEngine {

    /**
     * 探测视频基础参数。
     *
     * @param videoPath 本地视频路径
     * @return 媒体探测结果
     */
    MediaProbeResult probe(Path videoPath);
}
