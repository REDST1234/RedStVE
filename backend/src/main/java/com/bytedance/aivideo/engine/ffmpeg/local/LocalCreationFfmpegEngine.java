package com.bytedance.aivideo.engine.ffmpeg.local;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.FfmpegCommandProperties;
import com.bytedance.aivideo.engine.ffmpeg.api.CreationFfmpegEngine;
import com.bytedance.aivideo.engine.ffmpeg.model.CreationGridPageResult;
import com.bytedance.aivideo.engine.ffmpeg.model.LuminanceDetectResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LocalCreationFfmpegEngine implements CreationFfmpegEngine {

    private static final int MAX_OUTPUT_CAPTURE_CHARS = 12000;

    private final FfmpegCommandProperties ffmpegCommandProperties;

    private static final double DEFAULT_LUMINANCE_FALLBACK = 0.5;
    // 兼容 signalstats 原始输出格式: YAVG=123.45
    private static final Pattern YAVG_PATTERN = Pattern.compile("\\bYAVG=([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern YLOW_PATTERN = Pattern.compile("\\bYLOW=([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern YHIGH_PATTERN = Pattern.compile("\\bYHIGH=([0-9]+(?:\\.[0-9]+)?)");
    // 兼容 lavfi metadata 输出格式: lavfi.signalstats.YAVG=123.45
    private static final Pattern LAVFI_YAVG_PATTERN = Pattern.compile("lavfi\\.signalstats\\.YAVG=([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern LAVFI_YLOW_PATTERN = Pattern.compile("lavfi\\.signalstats\\.YLOW=([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern LAVFI_YHIGH_PATTERN = Pattern.compile("lavfi\\.signalstats\\.YHIGH=([0-9]+(?:\\.[0-9]+)?)");
    // 兼容 showinfo 输出格式: mean:[87 119 129]，取第一个 Y 均值
    private static final Pattern SHOWINFO_MEAN_PATTERN = Pattern.compile(
            "mean:\\[\\s*([0-9]+(?:\\.[0-9]+)?)\\s+[0-9]+(?:\\.[0-9]+)?\\s+[0-9]+(?:\\.[0-9]+)?\\s*\\]"
    );

    public LocalCreationFfmpegEngine(FfmpegCommandProperties ffmpegCommandProperties) {
        this.ffmpegCommandProperties = ffmpegCommandProperties;
    }

    @Override
    public LuminanceDetectResult detectLuminance(Path videoPath) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegCommandProperties.getPath());
        command.add("-i");
        command.add(videoPath.toAbsolutePath().toString());
        command.add("-r");
        command.add("1");
        command.add("-vf");
        // 通过 metadata=print 稳定输出 lavfi.signalstats.YLOW/YHIGH/YAVG，便于解析鲁棒对比度。
        command.add("signalstats,metadata=mode=print:file=-");
        command.add("-f");
        command.add("null");
        command.add("-");

        log.info("detectLuminance start: {}", String.join(" ", command));

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            double sumYavg = 0.0;
            int count = 0;
            double sumYlow = 0.0;
            int ylowCount = 0;
            double sumYhigh = 0.0;
            int yhighCount = 0;
            Set<String> matchedPatterns = new LinkedHashSet<>();
            StringBuilder sampleOutput = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendLineCapped(sampleOutput, line);
                    ParsedFrameStats parsed = extractLumaFromLine(line);
                    if (parsed != null) {
                        if (parsed.getYavg() != null) {
                            sumYavg += parsed.getYavg();
                            count++;
                        }
                        if (parsed.getYlow() != null) {
                            sumYlow += parsed.getYlow();
                            ylowCount++;
                        }
                        if (parsed.getYhigh() != null) {
                            sumYhigh += parsed.getYhigh();
                            yhighCount++;
                        }
                        if (parsed.getPatternType() != null && !parsed.getPatternType().isBlank()) {
                            matchedPatterns.add(parsed.getPatternType());
                        }
                    }
                }
            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("detectLuminance timeout for {}", videoPath);
            }

            if (count == 0) {
                log.warn("detectLuminance fallback used: videoPath={}, parsedCount=0, fallbackUsed=true, sampleOutput={}",
                        videoPath, sampleOutput);
                return new LuminanceDetectResult(
                        DEFAULT_LUMINANCE_FALLBACK,
                        null,
                        null,
                        null,
                        true,
                        0,
                        "NONE",
                        true
                );
            }

            double avg = sumYavg / count;
            // 归一化 (YUV亮度一般在 0~255)
            double normalized = avg / 255.0;
            Double yLowAvg = ylowCount > 0 ? (sumYlow / ylowCount) : null;
            Double yHighAvg = yhighCount > 0 ? (sumYhigh / yhighCount) : null;
            Double contrastRatio = null;
            boolean contrastUnavailable = true;
            if (yLowAvg != null && yHighAvg != null) {
                contrastRatio = (yHighAvg + 1.0) / (yLowAvg + 1.0);
                contrastUnavailable = false;
            }

            String matchedPatternType = matchedPatterns.isEmpty()
                    ? "UNKNOWN"
                    : String.join("|", matchedPatterns);

            if (contrastUnavailable) {
                log.warn("detectLuminance contrastUnavailable=true: videoPath={}, matchedPatternType={}, sampleCount={}, ylowCount={}, yhighCount={}",
                        videoPath, matchedPatternType, count, ylowCount, yhighCount);
            }
            log.info("detectLuminance success: videoPath={}, matchedPatternType={}, sampleCount={}, normalizedLuminance={}, yLowAvg={}, yHighAvg={}, contrastRatio={}",
                    videoPath, matchedPatternType, count, normalized, yLowAvg, yHighAvg, contrastRatio);
            return new LuminanceDetectResult(
                    normalized,
                    yLowAvg,
                    yHighAvg,
                    contrastRatio,
                    false,
                    count,
                    matchedPatternType,
                    contrastUnavailable
            );

        } catch (IOException | InterruptedException e) {
            log.error("detectLuminance failed", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BizException(ErrorCode.FFMPEG_ERROR, "Luminance detection failed");
        }
    }

    ParsedFrameStats extractLumaFromLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String upper = line.toUpperCase(Locale.ROOT);
        Double yavg = null;
        Double ylow = null;
        Double yhigh = null;
        Set<String> patternTypes = new LinkedHashSet<>();

        Double lavfiYavg = matchNumber(line, LAVFI_YAVG_PATTERN);
        Double lavfiYlow = matchNumber(line, LAVFI_YLOW_PATTERN);
        Double lavfiYhigh = matchNumber(line, LAVFI_YHIGH_PATTERN);
        if (lavfiYavg != null || lavfiYlow != null || lavfiYhigh != null) {
            yavg = lavfiYavg;
            ylow = lavfiYlow;
            yhigh = lavfiYhigh;
            patternTypes.add("LAVFI_SIGNALSTATS");
        }

        if (yavg == null) {
            Double rawYavg = matchNumber(line, YAVG_PATTERN);
            if (rawYavg != null) {
                yavg = rawYavg;
                patternTypes.add("YAVG");
            }
        }
        if (ylow == null) {
            Double rawYlow = matchNumber(line, YLOW_PATTERN);
            if (rawYlow != null) {
                ylow = rawYlow;
                patternTypes.add("YLOW");
            }
        }
        if (yhigh == null) {
            Double rawYhigh = matchNumber(line, YHIGH_PATTERN);
            if (rawYhigh != null) {
                yhigh = rawYhigh;
                patternTypes.add("YHIGH");
            }
        }

        if (yavg == null && upper.contains("MEAN:[")) {
            Double showinfoMeanY = matchNumber(line, SHOWINFO_MEAN_PATTERN);
            if (showinfoMeanY != null) {
                yavg = showinfoMeanY;
                patternTypes.add("SHOWINFO_MEAN");
            }
        }

        if (yavg == null && ylow == null && yhigh == null) {
            return null;
        }
        return new ParsedFrameStats(
                yavg,
                ylow,
                yhigh,
                patternTypes.isEmpty() ? "UNKNOWN" : String.join("|", patternTypes)
        );
    }

    private Double matchNumber(String line, Pattern pattern) {
        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public void generateGridSequence(Path videoPath, Path outputPath, int fps, int gridCols, int gridRows, int containerSize) {
        Path outputDir = outputPath.toAbsolutePath().getParent();
        if (outputDir == null) {
            throw new BizException(ErrorCode.FFMPEG_ERROR, "Grid output directory is invalid");
        }
        List<CreationGridPageResult> pages = generateGridSequencePages(
                videoPath,
                outputDir,
                stripExtension(outputPath.getFileName().toString()),
                fps,
                gridCols,
                gridRows,
                containerSize,
                1.0,
                1
        );
        if (pages.isEmpty()) {
            throw new BizException(ErrorCode.FFMPEG_ERROR, "Grid generation returns empty pages");
        }
        try {
            Files.copy(pages.get(0).getOutputPath(), outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.FFMPEG_ERROR, "Copy first grid page failed: " + ex.getMessage());
        }
    }

    @Override
    public List<CreationGridPageResult> generateGridSequencePages(
            Path videoPath,
            Path outputDir,
            String filePrefix,
            int fps,
            int gridCols,
            int gridRows,
            int containerSize,
            double totalDurationSeconds,
            int maxPages
    ) {
        if (fps <= 0 || gridCols <= 0 || gridRows <= 0 || containerSize <= 0) {
            throw new BizException(ErrorCode.FFMPEG_ERROR, "Invalid grid params");
        }

        int framesPerPage = gridCols * gridRows;
        int sampledFrameCount = Math.max(1, (int) Math.ceil(totalDurationSeconds * fps));
        int estimatedPages = (int) Math.ceil((double) sampledFrameCount / framesPerPage);
        int resolvedMaxPages = Math.max(1, maxPages);
        int pageCount = Math.max(1, Math.min(estimatedPages, resolvedMaxPages));

        try {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
        } catch (IOException ex) {
            throw new BizException(ErrorCode.FFMPEG_ERROR, "Create grid output dir failed: " + ex.getMessage());
        }

        List<CreationGridPageResult> results = new ArrayList<>();
        for (int pageIndex = 1; pageIndex <= pageCount; pageIndex++) {
            int start = (pageIndex - 1) * framesPerPage;
            int end = start + framesPerPage - 1;
            String fileName = String.format("%s_%03d.jpg", filePrefix, pageIndex);
            Path outputPath = outputDir.resolve(fileName);

            runPagedGridCommand(videoPath, outputPath, fps, gridCols, gridRows, containerSize, start, end);
            if (!Files.exists(outputPath)) {
                throw new BizException(ErrorCode.FFMPEG_ERROR,
                        "Grid generation failed: output not found, pageIndex=" + pageIndex);
            }
            results.add(new CreationGridPageResult(pageIndex, outputPath, framesPerPage));
        }

        return results;
    }

    private void runPagedGridCommand(
            Path videoPath,
            Path outputPath,
            int fps,
            int gridCols,
            int gridRows,
            int containerSize,
            int start,
            int end
    ) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegCommandProperties.getPath());
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-nostats");
        command.add("-y");
        command.add("-i");
        command.add(videoPath.toAbsolutePath().toString());
        command.add("-vf");

        String filter = String.format(
                "fps=%d,select='between(n\\,%d\\,%d)',drawtext=text='%%{pts\\:hms}':fontsize=48:fontcolor=yellow:box=1:boxcolor=black@0.5:x=20:y=20,scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2:black,tile=%dx%d",
                fps, start, end,
                containerSize, containerSize,
                containerSize, containerSize,
                gridCols, gridRows
        );

        command.add(filter);
        command.add("-frames:v");
        command.add("1");
        command.add(outputPath.toAbsolutePath().toString());

        log.info("generateGridSequence page start: {}", String.join(" ", command));

        runFfmpegWithTimeout(command);
    }

    private void runFfmpegWithTimeout(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            StringBuilder outputBuffer = new StringBuilder();
            Thread outputReader = startOutputReader(process, outputBuffer);
            int timeoutSeconds = Math.max(10, ffmpegCommandProperties.getOperationTimeoutSeconds());
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            outputReader.join(1000L);
            String processOutput = outputBuffer.toString();
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(3, TimeUnit.SECONDS);
                throw new BizException(ErrorCode.FFMPEG_ERROR, "Grid generation timeout, output=" + processOutput);
            }

            if (process.exitValue() != 0) {
                throw new BizException(ErrorCode.FFMPEG_ERROR,
                        "Grid generation failed with exit code " + process.exitValue() + ", output=" + processOutput);
            }

        } catch (IOException | InterruptedException e) {
            log.error("runFfmpegWithTimeout failed", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BizException(ErrorCode.FFMPEG_ERROR, "Grid Sequence generation failed");
        }
    }

    private Thread startOutputReader(Process process, StringBuilder outputBuffer) {
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                char[] chunk = new char[2048];
                int len;
                while ((len = reader.read(chunk)) != -1) {
                    appendCapped(outputBuffer, chunk, len);
                }
            } catch (IOException ignored) {
                // 进程销毁时读取中断属于正常行为。
            }
        }, "ffmpeg-grid-output-reader");
        outputReader.setDaemon(true);
        outputReader.start();
        return outputReader;
    }

    private void appendCapped(StringBuilder target, char[] chunk, int len) {
        if (target.length() >= MAX_OUTPUT_CAPTURE_CHARS) {
            return;
        }
        int available = MAX_OUTPUT_CAPTURE_CHARS - target.length();
        target.append(chunk, 0, Math.min(len, available));
    }

    private void appendLineCapped(StringBuilder target, String line) {
        if (target.length() >= MAX_OUTPUT_CAPTURE_CHARS) {
            return;
        }
        int available = MAX_OUTPUT_CAPTURE_CHARS - target.length();
        String withBreak = line + System.lineSeparator();
        if (withBreak.length() <= available) {
            target.append(withBreak);
        } else {
            target.append(withBreak, 0, available);
        }
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    static final class ParsedFrameStats {
        private final Double yavg;
        private final Double ylow;
        private final Double yhigh;
        private final String patternType;

        private ParsedFrameStats(Double yavg, Double ylow, Double yhigh, String patternType) {
            this.yavg = yavg;
            this.ylow = ylow;
            this.yhigh = yhigh;
            this.patternType = patternType;
        }

        Double getYavg() {
            return yavg;
        }

        Double getYlow() {
            return ylow;
        }

        Double getYhigh() {
            return yhigh;
        }

        String getPatternType() {
            return patternType;
        }
    }
}
