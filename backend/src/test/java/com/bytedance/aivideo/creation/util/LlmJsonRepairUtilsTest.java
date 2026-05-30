package com.bytedance.aivideo.creation.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmJsonRepairUtilsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRemoveUnexpectedClosingBraceInHighlightArray() throws Exception {
        String malformed = """
                {"semanticTags":{"overallStyle":"demo","suitableRoles":["hook","body"]},"highlights":[{"segmentId":"h_01","audioContext":"ok"}}]}
                """;

        String repaired = LlmJsonRepairUtils.normalizeJsonObjectText(malformed);
        JsonNode root = objectMapper.readTree(repaired);

        assertEquals("demo", root.path("semanticTags").path("overallStyle").asText());
        assertTrue(root.path("highlights").isArray());
        assertEquals(1, root.path("highlights").size());
        assertEquals("h_01", root.path("highlights").get(0).path("segmentId").asText());
    }

    @Test
    void shouldAppendMissingClosersWhenModelStopsTooEarly() throws Exception {
        String malformed = """
                {"semanticTags":{"overallStyle":"demo"},"highlights":[{"segmentId":"h_01"
                """;

        String repaired = LlmJsonRepairUtils.normalizeJsonObjectText(malformed);
        JsonNode root = objectMapper.readTree(repaired);

        assertEquals("demo", root.path("semanticTags").path("overallStyle").asText());
        assertEquals("h_01", root.path("highlights").get(0).path("segmentId").asText());
    }
}
