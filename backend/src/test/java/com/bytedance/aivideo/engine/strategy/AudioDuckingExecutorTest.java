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
        assertTrue(fragment.hasExplicitFilterComplex());
        assertTrue(fragment.getMapArgs().contains("-map"));
        assertTrue(fragment.getMapArgs().contains("[outa]"));

        String filter = fragment.getFilterComplex();
        assertTrue(filter.contains("sidechaincompress"));
        assertTrue(filter.contains("ratio=5.0")); // 1.0 / 0.2
        assertTrue(filter.contains("amix"));
    }

    @Test
    void testAudioDuckingWithoutAudio() {
        String inputContext = "{}";
        StrategyExecutionFragment fragment = executor.buildExecutionFragment(inputContext, false);

        String filter = fragment.getFilterComplex();
        // 无原声时，直接输出 bgm，或者利用 anullsrc 兜底
        assertTrue(filter.contains("[1:a]volume=1.0[outa]"));
    }
}
