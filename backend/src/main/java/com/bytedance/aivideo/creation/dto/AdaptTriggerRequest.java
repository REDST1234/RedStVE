package com.bytedance.aivideo.creation.dto;

import lombok.Data;

/**
 * 触发素材适配请求。
 */
@Data
public class AdaptTriggerRequest {

    /**
     * 可选编排版本号；不传时后端自动选最新版本。
     */
    private String versionId;
}
