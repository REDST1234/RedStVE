package com.bytedance.aivideo.creation.dto;

import lombok.Data;

/**
 * 触发槽位匹配请求。
 */
@Data
public class MatchTriggerRequest {

    /**
     * 可选编排版本号；不传时后端自动生成。
     */
    private String versionId;
}
