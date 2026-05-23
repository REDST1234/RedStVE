package com.bytedance.aivideo.infrastructure.ark;

import com.bytedance.aivideo.infrastructure.ark.model.ArkInputMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class ArkPayloadFactoryTest {

    @Test
    void shouldBuildAudioInputPayload() {
        ArkPayloadFactory factory = new ArkPayloadFactory();
        List<ArkInputMessage> messages = factory.buildAudioInput("AAAA", "mp3", "请识别音频");

        Assertions.assertEquals(1, messages.size());
        Assertions.assertEquals("user", messages.get(0).getRole());
        Assertions.assertEquals(2, messages.get(0).getContent().size());
        Assertions.assertEquals("input_audio", messages.get(0).getContent().get(0).getType());
        Assertions.assertEquals("AAAA", messages.get(0).getContent().get(0).getInputAudio().getData());
        Assertions.assertEquals("mp3", messages.get(0).getContent().get(0).getInputAudio().getFormat());
        Assertions.assertEquals("text", messages.get(0).getContent().get(1).getType());
        Assertions.assertEquals("请识别音频", messages.get(0).getContent().get(1).getText());
    }
}
