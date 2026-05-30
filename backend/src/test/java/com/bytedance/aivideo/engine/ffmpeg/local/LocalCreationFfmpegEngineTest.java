package com.bytedance.aivideo.engine.ffmpeg.local;

import com.bytedance.aivideo.config.FfmpegCommandProperties;
import com.bytedance.aivideo.engine.ffmpeg.model.LuminanceDetectResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalCreationFfmpegEngineTest {

    private static LocalCreationFfmpegEngine engine;
    private static Path testVideoPath;
    private static Path testOutputDir;

    @BeforeAll
    static void setup() throws IOException, InterruptedException {
        FfmpegCommandProperties properties = new FfmpegCommandProperties();
        properties.setPath("ffmpeg"); // 确保宿主机环境变量有 ffmpeg
        properties.setOperationTimeoutSeconds(60);

        engine = new LocalCreationFfmpegEngine(properties);
        
        testOutputDir = Paths.get("target/test-output");
        Files.createDirectories(testOutputDir);
        
        testVideoPath = testOutputDir.resolve("test_color.mp4");
        
        // 使用 ffmpeg 生成一个 3 秒的测试视频 (纯红背景)
        Process process = new ProcessBuilder(
                "ffmpeg", "-y", "-f", "lavfi", "-i", "color=c=red:s=1280x720:d=3",
                "-c:v", "libx264", testVideoPath.toString()
        ).redirectErrorStream(true).start();
        
        // 消耗流
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {}
        }
        process.waitFor();
    }

    @AfterAll
    static void cleanup() {
        if (Files.exists(testVideoPath)) {
            testVideoPath.toFile().delete();
        }
    }

    @Test
    void testDetectLuminance() {
        LuminanceDetectResult result = engine.detectLuminance(testVideoPath);
        Double luminance = result.getNormalizedLuminance();
        System.out.println("Luminance for red video: " + luminance);
        // 红色的 Y(亮度) 应该是常数，在 [0.1, 0.9] 之间
        assertNotNull(luminance);
        assertTrue(luminance > 0.1 && luminance < 0.9);
        assertFalse(result.isFallbackUsed());
        assertTrue(result.getSampleCount() > 0);
        assertNotNull(result.getMatchedPatternType());
        if (result.getContrastRatio() == null) {
            assertTrue(result.isContrastUnavailable());
        } else {
            assertTrue(result.getContrastRatio() >= 1.0);
            assertFalse(result.isContrastUnavailable());
        }
    }

    @Test
    void testGenerateGridSequence() {
        Path gridOutputPath = testOutputDir.resolve("test_grid.jpg");
        // 生成 2x4 宫格
        engine.generateGridSequence(testVideoPath, gridOutputPath, 1, 2, 4, 512);
        
        File gridFile = gridOutputPath.toFile();
        assertTrue(gridFile.exists());
        assertTrue(gridFile.length() > 0);
        System.out.println("Grid sequence generated successfully at: " + gridOutputPath);
    }

    @Test
    void testGenerateGridSequencePages() {
        List<com.bytedance.aivideo.engine.ffmpeg.model.CreationGridPageResult> pages = engine.generateGridSequencePages(
                testVideoPath,
                testOutputDir,
                "test_grid_pages",
                1,
                2,
                4,
                512,
                3.0,
                3
        );

        assertNotNull(pages);
        assertTrue(!pages.isEmpty());
        assertTrue(pages.size() >= 1);
        for (com.bytedance.aivideo.engine.ffmpeg.model.CreationGridPageResult page : pages) {
            assertTrue(Files.exists(page.getOutputPath()));
            assertTrue(page.getOutputPath().toFile().length() > 0);
        }
    }

    @Test
    void testExtractLumaFromLine_multiPattern() {
        LocalCreationFfmpegEngine.ParsedFrameStats yavg = engine.extractLumaFromLine("[Parsed_signalstats_0] YLOW=10.1 YAVG=123.4 YHIGH=241.3 SATAVG=11.2");
        assertNotNull(yavg);
        assertEquals(123.4, yavg.getYavg(), 0.0001);
        assertEquals(10.1, yavg.getYlow(), 0.0001);
        assertEquals(241.3, yavg.getYhigh(), 0.0001);
        assertTrue(yavg.getPatternType().contains("YAVG"));
        assertTrue(yavg.getPatternType().contains("YLOW"));
        assertTrue(yavg.getPatternType().contains("YHIGH"));

        LocalCreationFfmpegEngine.ParsedFrameStats lavfi = engine.extractLumaFromLine("lavfi.signalstats.YAVG=121.5 lavfi.signalstats.YLOW=11 lavfi.signalstats.YHIGH=233");
        assertNotNull(lavfi);
        assertEquals(121.5, lavfi.getYavg(), 0.0001);
        assertEquals(11.0, lavfi.getYlow(), 0.0001);
        assertEquals(233.0, lavfi.getYhigh(), 0.0001);
        assertTrue(lavfi.getPatternType().contains("LAVFI_SIGNALSTATS"));

        LocalCreationFfmpegEngine.ParsedFrameStats showinfo = engine.extractLumaFromLine(
                "[Parsed_showinfo_1 @ 000001] n: 0 mean:[87 119 129] stdev:[26.0 14.8 7.7]");
        assertNotNull(showinfo);
        assertEquals(87.0, showinfo.getYavg(), 0.0001);
        assertNull(showinfo.getYlow());
        assertNull(showinfo.getYhigh());
        assertEquals("SHOWINFO_MEAN", showinfo.getPatternType());
    }

    @Test
    void testDetectLuminance_fallbackWhenNoMatchOutput() throws IOException {
        Path fakeCmd = testOutputDir.resolve("fake-ffmpeg.cmd");
        Files.writeString(
                fakeCmd,
                "@echo off\r\necho no-luma-here\r\nexit /b 0\r\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        FfmpegCommandProperties fakeProps = new FfmpegCommandProperties();
        fakeProps.setPath(fakeCmd.toAbsolutePath().toString());
        fakeProps.setOperationTimeoutSeconds(10);
        LocalCreationFfmpegEngine fakeEngine = new LocalCreationFfmpegEngine(fakeProps);

        LuminanceDetectResult result = fakeEngine.detectLuminance(testVideoPath);
        assertNotNull(result);
        assertEquals(0.5, result.getNormalizedLuminance(), 0.0001);
        assertTrue(result.isFallbackUsed());
        assertEquals(0, result.getSampleCount());
        assertEquals("NONE", result.getMatchedPatternType());
        assertNull(result.getContrastRatio());
        assertTrue(result.isContrastUnavailable());
    }
}
