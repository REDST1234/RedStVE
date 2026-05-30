package com.bytedance.aivideo.engine.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartCropExecutorTest {

    private final SmartCropExecutor executor = new SmartCropExecutor(new ObjectMapper());

    @Test
    void shouldBuildSafeCropAndScaleFilterForBoundingBox() {
        String inputContext = """
                {
                  "targetWidth": 1080,
                  "targetHeight": 1920,
                  "sourceWidth": 1280,
                  "sourceHeight": 720,
                  "boundingBox": {
                    "x": 100,
                    "y": 40,
                    "w": 720,
                    "h": 480
                  }
                }
                """;

        StrategyExecutionFragment fragment = executor.buildExecutionFragment(inputContext, true);
        String filter = fragment.getVideoFilters().getFirst();
        assertTrue(filter.startsWith("crop="));
        assertTrue(filter.contains("scale=1080:1920"));
        assertTrue(filter.contains("max(0\\,min("));
        assertTrue(filter.contains("460.000000"));
        assertTrue(filter.contains("280.000000"));
        assertEquals(StrategyOutputKind.VIDEO_OUTPUT, fragment.getOutputKind());
    }

    @Test
    void shouldFallbackToCenterCropWhenBoundingBoxMissing() {
        String inputContext = """
                {
                  "targetWidth": 1080,
                  "targetHeight": 1920,
                  "sourceWidth": 1280,
                  "sourceHeight": 720
                }
                """;

        StrategyExecutionFragment fragment = executor.buildExecutionFragment(inputContext, false);
        String filter = fragment.getVideoFilters().getFirst();
        assertTrue(filter.contains("(iw-if(gte(iw/ih\\,0.562500)\\,floor(ih*0.562500)\\,iw))/2"));
        assertTrue(filter.contains("(ih-if(gte(iw/ih\\,0.562500)\\,ih\\,floor(iw/0.562500)))/2"));
        assertTrue(filter.endsWith("scale=1080:1920"));
    }
}
