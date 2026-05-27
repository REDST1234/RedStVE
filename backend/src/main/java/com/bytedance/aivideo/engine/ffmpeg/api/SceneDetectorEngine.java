package com.bytedance.aivideo.engine.ffmpeg.api;

import com.bytedance.aivideo.engine.ffmpeg.model.SceneDetectResult;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * 镜头切分引擎接口。
 * 负责调用底层处理程序（如 FFmpeg）按指定阈值对视频进行场景切换检测。
 */
public interface SceneDetectorEngine {
    
    /**
     * 异步探测视频镜头切点
     * 
     * @param videoPath 视频本地绝对路径
     * @param threshold 场景切换阈值（如 0.3。高敏模式下可传入 0.1 等极低值，依赖后置过滤）
     * @return 包含切分结果的异步 Future
     */
    CompletableFuture<SceneDetectResult> detectScenesAsync(Path videoPath, double threshold);
}
