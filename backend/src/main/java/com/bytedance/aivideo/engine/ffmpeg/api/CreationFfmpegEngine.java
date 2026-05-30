package com.bytedance.aivideo.engine.ffmpeg.api;

import com.bytedance.aivideo.engine.ffmpeg.model.CreationGridPageResult;
import com.bytedance.aivideo.engine.ffmpeg.model.LuminanceDetectResult;

import java.nio.file.Path;
import java.util.List;

/**
 * 创作链路专用的 FFmpeg 算子引擎
 */
public interface CreationFfmpegEngine {

    /**
     * 极速提取全片亮度均值 (YAVG)
     * @param videoPath 视频路径
     * @return 检测结果（含 0.0 ~ 1.0 均值及 fallback 信息）
     */
    LuminanceDetectResult detectLuminance(Path videoPath);

    /**
     * 提取宫格拼图 (Grid Sequence)
     * @param videoPath 源视频路径
     * @param outputPath 宫格大图输出路径
     * @param fps 抽样率 (如 1)
     * @param gridCols 宫格列数
     * @param gridRows 宫格行数
     * @param containerSize 单帧安全容器大小 (如 512)
     */
    void generateGridSequence(Path videoPath, Path outputPath, int fps, int gridCols, int gridRows, int containerSize);

    /**
     * 分页提取宫格拼图（素材:宫格 = 1:N）。
     *
     * @param videoPath 源视频路径
     * @param outputDir 输出目录
     * @param filePrefix 文件名前缀（如 bizId + "_grid"）
     * @param fps 抽样率
     * @param gridCols 宫格列数
     * @param gridRows 宫格行数
     * @param containerSize 单帧安全容器大小
     * @param totalDurationSeconds 视频总时长
     * @param maxPages 最大分页数
     * @return 分页宫格结果（按 pageIndex 升序）
     */
    List<CreationGridPageResult> generateGridSequencePages(
            Path videoPath,
            Path outputDir,
            String filePrefix,
            int fps,
            int gridCols,
            int gridRows,
            int containerSize,
            double totalDurationSeconds,
            int maxPages
    );
}
