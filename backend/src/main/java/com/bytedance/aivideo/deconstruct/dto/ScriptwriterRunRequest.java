package com.bytedance.aivideo.deconstruct.dto;

import lombok.Data;

import java.util.List;

/**
 * 编剧运行请求。
 */
@Data
public class ScriptwriterRunRequest {

    private String snapshotId;

    /**
     * 可选素材列表，未传时默认使用项目关联素材。
     */
    private List<String> materialBizIds;
}
