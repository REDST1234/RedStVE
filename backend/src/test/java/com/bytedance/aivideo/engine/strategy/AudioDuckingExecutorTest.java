package com.bytedance.aivideo.engine.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AudioDuckingExecutorTest {

    private final AudioDuckingExecutor executor = new AudioDuckingExecutor(new ObjectMapper());

    @Test
    void testAudioDuckingWithAudio() {
        String inputContext = "{\"duckingRatio\": 0.2}";
        StrategyExecutionFragment fragment = executor.buildExecutionFragment(inputContext, true);

        assertEquals(StrategyOutputKind.VIDEO_OUTPUT, fragment.getOutputKind());
        assertFalse(fragment.hasExplicitFilterComplex()); // NO-OP doesn't use explicit filter complex
        
        // Assert that the deferred logic added volume=1.0 to audioFilters
        assertTrue(fragment.getAudioFilters().contains("volume=1.0"));
    }

    @Test
    void testAudioDuckingWithoutAudio() {
        String inputContext = "{}";
        StrategyExecutionFragment fragment = executor.buildExecutionFragment(inputContext, false);

        assertFalse(fragment.hasExplicitFilterComplex());
        assertTrue(fragment.getAudioFilters().contains("volume=1.0"));
    }
}
