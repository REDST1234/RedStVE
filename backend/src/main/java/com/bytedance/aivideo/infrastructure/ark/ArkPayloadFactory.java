package com.bytedance.aivideo.infrastructure.ark;

import com.bytedance.aivideo.infrastructure.ark.model.ArkInputContent;
import com.bytedance.aivideo.infrastructure.ark.model.ArkInputAudio;
import com.bytedance.aivideo.infrastructure.ark.model.ArkInputMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 方舟多模态 payload 构造器。
 */
@Component
public class ArkPayloadFactory {

    /**
     * 构造音频识别请求输入。
     */
    public List<ArkInputMessage> buildAudioInput(String base64Audio, String format, String promptText) {
        ArkInputAudio inputAudio = new ArkInputAudio();
        inputAudio.setData(base64Audio);
        inputAudio.setFormat(format);

        ArkInputContent audioContent = new ArkInputContent();
        audioContent.setType("input_audio");
        audioContent.setInputAudio(inputAudio);

        ArkInputContent textContent = new ArkInputContent();
        textContent.setType("text");
        textContent.setText(promptText);

        ArkInputMessage message = new ArkInputMessage();
        message.setRole("user");
        message.setContent(List.of(audioContent, textContent));
        return List.of(message);
    }

    /**
     * 预留：后续 OCR 可直接复用 image + prompt 协议构造。
     */
    public List<ArkInputMessage> buildImageInput(String imageUrl, String promptText) {
        ArkInputContent imageContent = new ArkInputContent();
        imageContent.setType("input_image");
        imageContent.setImageUrl(imageUrl);

        ArkInputContent textContent = new ArkInputContent();
        textContent.setType("text");
        textContent.setText(promptText);

        ArkInputMessage message = new ArkInputMessage();
        message.setRole("user");
        message.setContent(List.of(imageContent, textContent));
        return List.of(message);
    }
}
