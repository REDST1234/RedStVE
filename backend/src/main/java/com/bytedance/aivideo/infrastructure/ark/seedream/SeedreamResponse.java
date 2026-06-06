package com.bytedance.aivideo.infrastructure.ark.seedream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Seedream 图片生成响应体。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SeedreamResponse {

    /**
     * 生成的图片数据列表。
     */
    private List<ImageData> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageData {

        /** 图片公网 URL（response_format=url 时返回）。 */
        private String url;

        /** Base64 图片数据（response_format=b64_json 时返回）。 */
        @JsonProperty("b64_json")
        private String b64Json;
    }
}
