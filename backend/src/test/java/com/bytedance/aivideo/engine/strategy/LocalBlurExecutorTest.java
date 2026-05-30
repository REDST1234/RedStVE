package com.bytedance.aivideo.engine.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalBlurExecutorTest {

    private final LocalBlurExecutor executor = new LocalBlurExecutor(new ObjectMapper());

    @Test
    void shouldBuildBackgroundBlurFilterComplex() {
        String inputContext = """
                {
                  "sourceWidth": 1200,
                  "sourceHeight": 1600,
                  "boundingBox": {
                    "x": 120,
                    "y": 240,
                    "w": 600,
                    "h": 800
                  },
                  "blurStrength": 18,
                  "featherPercent": 10
                }
                """;

        StrategyExecutionFragment fragment = executor.buildExecutionFragment(inputContext, false);

        assertEquals(StrategyOutputKind.IMAGE_OUTPUT, fragment.getOutputKind());
        assertEquals("jpg", fragment.getPreferredExtension());
        assertTrue(fragment.hasExplicitFilterComplex());
        assertTrue(fragment.getFilterComplex().contains("boxblur"));
        assertTrue(fragment.getFilterComplex().contains("overlay="));
        assertTrue(fragment.getMapArgs().contains("[outv]"));
    }

    @Test
    void shouldReturnEmptyFragmentWhenBoundingBoxInvalid() {
        String inputContext = """
                {
                  "sourceWidth": 1200,
                  "sourceHeight": 1600,
                  "boundingBox": {
                    "x": -1,
                    "y": 10,
                    "w": 0,
                    "h": 800
                  }
                }
                """;

        StrategyExecutionFragment fragment = executor.buildExecutionFragment(inputContext, false);

        assertTrue(fragment.getFilterComplex() == null || fragment.getFilterComplex().isBlank());
        assertTrue(fragment.getMapArgs().isEmpty());
    }
}
