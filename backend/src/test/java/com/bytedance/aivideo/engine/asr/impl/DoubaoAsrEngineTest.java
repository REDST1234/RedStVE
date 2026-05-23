package com.bytedance.aivideo.engine.asr.impl;

import com.bytedance.aivideo.config.ArkProperties;
import com.bytedance.aivideo.config.AsrProperties;
import com.bytedance.aivideo.engine.asr.model.AsrTranscriptionResult;
import com.bytedance.aivideo.infrastructure.ark.ArkPayloadFactory;
import com.bytedance.aivideo.infrastructure.ark.ArkResponsesClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;

class DoubaoAsrEngineTest {

    @Test
    void shouldParseAsrSegmentsFromChatCompletionsOutput() throws Exception {
        ArkResponsesClient responsesClient = Mockito.mock(ArkResponsesClient.class);
        ArkPayloadFactory payloadFactory = new ArkPayloadFactory();
        AsrProperties asrProperties = new AsrProperties();
        ArkProperties arkProperties = new ArkProperties();
        arkProperties.setMaxRetries(0);
        ObjectMapper objectMapper = new ObjectMapper();

        String outputJsonBlock1 = "{\"fullText\":\"你好 世界\",\"segments\":[{\"start\":0.0,\"end\":1.2,\"text\":\"你好\",\"speaker\":\"SPEAKER_1\",\"confidence\":0.99},{\"start\":1.2,\"end\":2.0,\"text\":\"世界\",\"speaker\":\"SPEAKER_2\",\"confidence\":0.98}]}";
        JsonNode mockResponse = objectMapper.readTree(
                "{\"choices\":[{\"message\":{\"content\":" + objectMapper.writeValueAsString(outputJsonBlock1) + "}}]}"
        );
        Mockito.when(responsesClient.createResponse(Mockito.any())).thenReturn(mockResponse);

        DoubaoAsrEngine engine = new DoubaoAsrEngine(
                responsesClient,
                payloadFactory,
                asrProperties,
                arkProperties,
                objectMapper
        );

        Path tempAudio = Files.createTempFile("asr-test", ".mp3");
        Files.write(tempAudio, "fake-audio-content".getBytes());
        try {
            AsrTranscriptionResult result = engine.transcribe(tempAudio, "task-test");
            Assertions.assertEquals("你好 世界", result.getFullText());
            Assertions.assertEquals(2, result.getSegments().size());
            Assertions.assertEquals("你好", result.getSegments().get(0).getText());
            Assertions.assertEquals(0.0, result.getSegments().get(0).getStartSec());
            Assertions.assertEquals(1.2, result.getSegments().get(0).getEndSec());
            Assertions.assertEquals("SPEAKER_1", result.getSegments().get(0).getSpeakerLabel());
            Assertions.assertEquals("世界", result.getSegments().get(1).getText());
            Assertions.assertEquals("SPEAKER_2", result.getSegments().get(1).getSpeakerLabel());
        } finally {
            Files.deleteIfExists(tempAudio);
        }
    }
}
