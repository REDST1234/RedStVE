package com.bytedance.aivideo.infrastructure.ark.model;

import lombok.Data;

import java.util.List;

/**
 * 方舟 chat/completions 协议请求体。
 */
@Data
public class ArkResponseRequest {

    private String model;

    private List<ArkInputMessage> messages;

    private ArkThinking thinking;
}
