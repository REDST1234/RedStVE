package com.bytedance.aivideo.engine.ffmpeg.api;

import java.nio.file.Path;

/**
 * 音轨提取引擎。
 */
public interface AudioExtractEngine {

    /**
     * 将视频音轨提取为 16k 单声道 MP3。
     *
     * @param videoPath  视频路径
     * @param outputDir  输出目录
     * @param outputName 输出文件名（不含路径）
     * @return 音频文件路径
     */
    Path extractToMp3(Path videoPath, Path outputDir, String outputName);
}

