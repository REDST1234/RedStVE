package com.bytedance.aivideo.infrastructure.ark.model;

import lombok.Data;

import java.util.List;

/**
 * 方舟输入消息。
 */
@Data
public class ArkInputMessage {

    private String role;

    private List<ArkInputContent> content;
}

