package com.bytedance.aivideo.deconstruct.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 编剧运行响应。
 */
@Data
public class ScriptwriterRunResponse {

    private String runId;

    private String projectId;

    private String snapshotId;

    private String status;

    private List<String> materialBizIds;

    private String finalPromptText;

    private Map<String, Object> writerOutput;

    private OffsetDateTime createdAt;
}
