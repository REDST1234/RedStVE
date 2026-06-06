package com.bytedance.aivideo.engine.seedream;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.infrastructure.ark.seedream.SeedreamImageClient;
import com.bytedance.aivideo.infrastructure.ark.seedream.SeedreamRequest;
import com.bytedance.aivideo.infrastructure.ark.seedream.SeedreamResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Seedream 图片生成业务服务。
 * <p>
 * 封装 {@link SeedreamImageClient}，提供带降级/校验的图片生成能力。
 * 当前为 MVP 版本——单图生成，返回第一张图的 URL。
 */
@Service
@Slf4j
public class SeedreamImageService {

    private final SeedreamImageClient seedreamImageClient;

    public SeedreamImageService(SeedreamImageClient seedreamImageClient) {
        this.seedreamImageClient = seedreamImageClient;
    }

    /**
     * 生成单张图片，返回第一个结果的 URL。
     *
     * @param prompt 正向提示词
     * @return 生成的图片公网 URL
     */
    public String generateImageUrl(String prompt) {
        return generateImageUrl(prompt, null);
    }

    /**
     * 生成单张图片（含负向提示词），返回第一个结果的 URL。
     *
     * @param prompt         正向提示词
     * @param negativePrompt 负向提示词（可选）
     * @return 生成的图片公网 URL
     */
    public String generateImageUrl(String prompt, String negativePrompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Seedream 生图 prompt 不能为空");
        }

        SeedreamRequest request = SeedreamRequest.of(null, prompt);
        request.setNegativePrompt(negativePrompt);

        SeedreamResponse response = seedreamImageClient.generate(request);

        String url = response.getData().stream()
                .filter(item -> item.getUrl() != null && !item.getUrl().isBlank())
                .map(SeedreamResponse.ImageData::getUrl)
                .findFirst()
                .orElse(null);

        if (url == null) {
            throw new BizException(ErrorCode.ARK_API_ERROR,
                    "Seedream 生图成功但未返回有效 URL");
        }
        log.info("seedream image url: {}", url);
        return url;
    }

    /**
     * 批量生成图片，返回所有 URL。
     *
     * @param prompt 正向提示词
     * @return URL 列表（保持生成顺序）
     */
    public List<String> generateImageUrls(String prompt) {
        SeedreamRequest request = SeedreamRequest.of(null, prompt);
        SeedreamResponse response = seedreamImageClient.generate(request);

        return response.getData().stream()
                .filter(item -> item.getUrl() != null && !item.getUrl().isBlank())
                .map(SeedreamResponse.ImageData::getUrl)
                .collect(Collectors.toList());
    }
}
