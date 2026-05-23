package com.bytedance.aivideo.video.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 拆解结果三表写入命令。
 */
@Data
public class AnalysisResultWriteCommand {

    private Long bizId;

    private String taskId;

    private String status;

    private String categoryId;

    private String llmModelUsed;

    private String schemaVersion;

    private List<String> partialFailedDimensions;

    private BigDecimal durationSec;

    private Integer width;

    private Integer height;

    private BigDecimal fps;

    private String videoCodec;

    private String audioCodec;

    private Integer hasAudio;

    private Long bitrate;

    private Integer shotCount;

    private Integer asrSegmentCount;

    private Integer keyFrameCount;

    private String asrFullText;

    private String ocrFullText;

    private String transcriptSummaryText;

    private String keywordsText;

    private String shotSummaryJson;

    private String timelineLogJson;

    private String scriptStructureJson;

    private String rhythmStructureJson;

    private String packagingStructureJson;

    private String llmTokenUsageJson;

    private String provenanceJson;
}
