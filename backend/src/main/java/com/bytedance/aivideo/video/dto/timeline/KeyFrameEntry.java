package com.bytedance.aivideo.video.dto.timeline;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KeyFrameEntry {
    private String frameRole;
    private String timestamp;
    private String filePath;
}
