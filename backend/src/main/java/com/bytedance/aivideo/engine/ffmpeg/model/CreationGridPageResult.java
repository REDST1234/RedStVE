package com.bytedance.aivideo.engine.ffmpeg.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreationGridPageResult {
    private int pageIndex;
    private Path outputPath;
    private int frameCount;
}
