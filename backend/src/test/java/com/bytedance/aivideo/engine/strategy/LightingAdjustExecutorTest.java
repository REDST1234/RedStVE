package com.bytedance.aivideo.engine.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightingAdjustExecutorTest {

    private final LightingAdjustExecutor executor = new LightingAdjustExecutor(new ObjectMapper());

    @Test
    void shouldBuildEqFilterAndClampExtremes() {
        String inputContext = """
                {
                  "brightnessPercent": 80,
                  "contrastPercent": 90
                }
                """;

        StrategyExecutionFragment fragment = executor.buildExecutionFragment(inputContext, true);

        assertEquals(StrategyOutputKind.VIDEO_OUTPUT, fragment.getOutputKind());
        assertTrue(fragment.isPreserveAudio());
        assertEquals(1, fragment.getVideoFilters().size());
        assertEquals("eq=brightness=0.3000:contrast=1.6000", fragment.getVideoFilters().getFirst());
    }

    @Test
    void shouldReturnEmptyEffectWhenParametersAreNeutral() {
        StrategyExecutionFragment fragment = executor.buildExecutionFragment("{}", false);

        assertTrue(fragment.getVideoFilters().isEmpty());
        assertEquals(StrategyOutputKind.VIDEO_OUTPUT, fragment.getOutputKind());
        assertTrue(fragment.getExtraArgs().isEmpty());
    }
}
