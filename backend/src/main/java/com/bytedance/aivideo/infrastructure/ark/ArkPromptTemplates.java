package com.bytedance.aivideo.infrastructure.ark;

/**
 * 方舟提示词模板集中维护类。
 * 约定：ASR/OCR/LLM 相关提示词统一在这里维护，避免分散硬编码。
 */
public final class ArkPromptTemplates {

    private ArkPromptTemplates() {
    }

    /**
     * ASR 基础提示词：要求输出统一 JSON 结构，便于直接入库。
     */
    public static final String ASR_TRANSCRIBE_JSON =
            "请识别音频中的内容，并严格输出JSON。"
                    + "必须包含完整转写 fullText，以及按时间顺序的 segments 数组。"
                    + "每个 segment 必须包含 start、end、text、speaker、confidence。"
                    + "speaker 建议值示例：SPEAKER_1、SPEAKER_2；若无法区分说话人请填 UNKNOWN。"
                    + "输出格式为"
                    + "{\"fullText\":\"\",\"segments\":[{\"start\":0.0,\"end\":1.0,\"text\":\"\",\"speaker\":\"SPEAKER_1\",\"confidence\":0.99}]}";

    /**
     * ASR 二次重试提示词后缀：仅做格式修正。
     */
    public static final String STRICT_JSON_SUFFIX = "仅输出JSON，不要包含解释、代码块标记或其他文本。";
}
