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

    public List<ArkInputMessage> buildMultimodalInput(List<String> base64Images, String promptText) {
        List<ArkInputContent> contents = new java.util.ArrayList<>();
        
        if (base64Images != null) {
            for (String base64 : base64Images) {
                ArkInputContent imageContent = new ArkInputContent();
                imageContent.setType("image_url");
                ArkInputContent.ArkImageUrl imageUrl = new ArkInputContent.ArkImageUrl();
                imageUrl.setUrl("data:image/jpeg;base64," + base64);
                imageContent.setImageUrl(imageUrl);
                contents.add(imageContent);
            }
        }

        ArkInputContent textContent = new ArkInputContent();
        textContent.setType("text");
        textContent.setText(promptText);
        contents.add(textContent);

        ArkInputMessage message = new ArkInputMessage();
        message.setRole("user");
        message.setContent(contents);
        return List.of(message);
    }
}
