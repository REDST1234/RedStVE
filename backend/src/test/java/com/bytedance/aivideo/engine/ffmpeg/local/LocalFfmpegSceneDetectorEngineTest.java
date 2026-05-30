package com.bytedance.aivideo.engine.ffmpeg.local;

import com.bytedance.aivideo.config.FfmpegCommandProperties;
import com.bytedance.aivideo.engine.ffmpeg.api.MediaProbeEngine;
import com.bytedance.aivideo.engine.ffmpeg.model.MediaProbeResult;
import com.bytedance.aivideo.engine.ffmpeg.model.SceneDetectResult;
import com.bytedance.aivideo.engine.ffmpeg.model.SceneShot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class LocalFfmpegSceneDetectorEngineTest {

    @Mock
    private FfmpegCommandProperties ffmpegProperties;

    @Mock
    private MediaProbeEngine mediaProbeEngine;

    @InjectMocks
    private LocalFfmpegSceneDetectorEngine engine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // 配置本地 ffmpeg 路径。确保开发机上配置了环境变量，或提供绝对路径
        when(ffmpegProperties.getPath()).thenReturn("ffmpeg");

        // 模拟媒体探测返回时长
        MediaProbeResult dummyResult = new MediaProbeResult();
        dummyResult.setDuration(10.0); // 假定视频为 10 秒
        when(mediaProbeEngine.probe(any(Path.class))).thenReturn(dummyResult);
    }

    @Test
    void testDetectScenesAsync() throws Exception {
        // 提供一个可以用于测试的示例视频。注意：如果您的环境里没有这个视频，测试可能会失败。
        // 请换成实际存在的视频文件来跑真实验证。
        Path testVideo = Paths.get("src", "test", "resources", "test_video.mp4");

        // 如果文件不存在，可以跳过真实 FFmpeg 调用测试
        if (!testVideo.toFile().exists()) {
            System.out.println("Test video not found: " + testVideo.toAbsolutePath() + ", skip actual ffmpeg test.");
            return;
        }

        // 以高敏感阈值（例如 0.1 即 10.0 的 scdet threshold）测试
        CompletableFuture<SceneDetectResult> future = engine.detectScenesAsync(testVideo, 0.1);
        
        SceneDetectResult result = future.get();
        
        assertNotNull(result);
        assertNotNull(result.getShots());
        assertTrue(result.getTotalShots() > 0, "Shot list should not be empty");
        
        System.out.println("Total detected shots: " + result.getTotalShots());
        
        // 验证时间戳连贯性
        double expectedStart = 0.0;
        for (SceneShot shot : result.getShots()) {
            System.out.printf("Shot [%d] %f -> %f (Score: %f)%n", 
                shot.getShotIndex(), shot.getStartTime(), shot.getEndTime(), shot.getSceneScore());
                
            assertEquals(expectedStart, shot.getStartTime(), 0.001);
            assertTrue(shot.getDuration() > 0);
            expectedStart = shot.getEndTime();
        }
        
        // 最后一个 shot 应该闭合到总时长
        assertEquals(10.0, expectedStart, 0.001);
    }
}
