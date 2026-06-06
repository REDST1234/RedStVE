package com.bytedance.aivideo.infrastructure.ark.seedream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Seedream 图片生成请求体，对齐 {@code POST /api/v3/images/generations} 协议。
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeedreamRequest {

    /** 模型 ID，例如 {@code doubao-seedream-4-0-250828}。 */
    private String model;

    /** 正向提示词。 */
    private String prompt;

    /** 负向提示词（可选）。 */
    @JsonProperty("negative_prompt")
    private String negativePrompt;

    /** 出品尺寸：{@code 2K} / {@code 4K} 等（可选）。 */
    private String size;

    /** 是否开启水印（可选）。 */
    private Boolean watermark;

    /** 响应格式：{@code url} 或 {@code b64_json}（可选）。 */
    @JsonProperty("response_format")
    private String responseFormat;

    /** 组图模式：{@code disabled} 或 {@code auto}（可选）。 */
    @JsonProperty("sequential_image_generation")
    private String sequentialImageGeneration;

    /** 是否流式（可选）。 */
    private Boolean stream;

    /**
     * 快捷构造——仅必填字段。
     */
    public static SeedreamRequest of(String model, String prompt) {
        SeedreamRequest req = new SeedreamRequest();
        req.model = model;
        req.prompt = prompt;
        return req;
    }
}
