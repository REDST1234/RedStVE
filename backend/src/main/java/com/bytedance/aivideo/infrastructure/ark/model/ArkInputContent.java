package com.bytedance.aivideo.infrastructure.ark.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 方舟输入内容块。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArkInputContent {

    private String type;

    @JsonProperty("audio_url")
    private String audioUrl;

    @JsonProperty("input_audio")
    private ArkInputAudio inputAudio;

    private String text;

    @JsonProperty("image_url")
    private String imageUrl;
}
