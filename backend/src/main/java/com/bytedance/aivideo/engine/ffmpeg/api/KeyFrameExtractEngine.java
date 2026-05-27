package com.bytedance.aivideo.engine.ffmpeg.api;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 视频关键帧抽取引擎。
 */
public interface KeyFrameExtractEngine {

    /**
     * 根据提供的时间戳列表，从视频中抽取指定数量的关键帧，并保存到输出目录中。
     *
     * @param videoPath  源视频路径
     * @param timestamps 需要抽取的时间点列表（单位：秒）
     * @param outputDir  抽取出的图片存放目录
     * @return 抽取的图片路径列表
     */
    CompletableFuture<List<Path>> extractFramesAsync(Path videoPath, List<Double> timestamps, Path outputDir);
}
