package com.bytedance.aivideo.creation.service.impl;

import com.bytedance.aivideo.creation.mapper.CreativeMaterialMapper;
import com.bytedance.aivideo.creation.mapper.OutboxMessageMapper;
import com.bytedance.aivideo.creation.mapper.RenderRecordMapper;
import com.bytedance.aivideo.creation.mapper.SlotMatchResultMapper;
import com.bytedance.aivideo.creation.service.CreationProjectBgmBindingService;
import com.bytedance.aivideo.creation.service.CreationTemplateSnapshotService;
import com.bytedance.aivideo.deconstruct.service.DeconstructTemplateService;
import com.bytedance.aivideo.engine.remotion.RemotionServiceClient;
import com.bytedance.aivideo.engine.remotion.VideoOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CreationProjectServiceImplStateMachineTest {

    private final CreationProjectServiceImpl service = new CreationProjectServiceImpl(
            mock(DeconstructTemplateService.class),
            mock(CreationTemplateSnapshotService.class),
            mock(VideoOrchestrationService.class),
            mock(RemotionServiceClient.class),
            null,
            new ObjectMapper(),
            mock(CreativeMaterialMapper.class),
            mock(SlotMatchResultMapper.class),
            mock(CreationProjectBgmBindingService.class),
            mock(RenderRecordMapper.class),
            mock(OutboxMessageMapper.class)
    );

    @Test
    void shouldOnlyAcceptKnownRuntimeStatuses() {
        assertEquals("QUEUED", normalize("QUEUED"));
        assertEquals("RENDERING", normalize("RENDERING"));
        assertEquals("DONE", normalize("DONE"));
        assertEquals("FAILED", normalize("FAILED"));
        assertNull(normalize("CREATED"));
        assertNull(normalize("GENERATING"));
        assertNull(normalize(""));
        assertNull(normalize(null));
    }

    @Test
    void shouldAllowOnlyForwardTransitionsFromCreatedAndQueued() {
        assertTrue(canAdvance("CREATED", "QUEUED"));
        assertTrue(canAdvance("CREATED", "RENDERING"));
        assertTrue(canAdvance("CREATED", "DONE"));
        assertTrue(canAdvance("CREATED", "FAILED"));
        assertFalse(canAdvance("CREATED", "CREATED"));

        assertTrue(canAdvance("QUEUED", "QUEUED"));
        assertTrue(canAdvance("QUEUED", "RENDERING"));
        assertTrue(canAdvance("QUEUED", "DONE"));
        assertTrue(canAdvance("QUEUED", "FAILED"));
        assertFalse(canAdvance("QUEUED", "CREATED"));
    }

    @Test
    void shouldProtectTerminalAndRuntimeTransitions() {
        assertTrue(canAdvance("RENDERING", "RENDERING"));
        assertTrue(canAdvance("RENDERING", "DONE"));
        assertTrue(canAdvance("RENDERING", "FAILED"));
        assertFalse(canAdvance("RENDERING", "QUEUED"));

        assertTrue(canAdvance("DONE", "DONE"));
        assertFalse(canAdvance("DONE", "FAILED"));
        assertTrue(canAdvance("FAILED", "FAILED"));
        assertFalse(canAdvance("FAILED", "DONE"));

        assertTrue(canAdvance(null, "QUEUED"));
        assertFalse(canAdvance("CREATED", null));
    }

    private String normalize(String rawStatus) {
        return ReflectionTestUtils.invokeMethod(service, "normalizeRenderRuntimeStatus", rawStatus);
    }

    private boolean canAdvance(String currentStatus, String nextStatus) {
        Boolean result = ReflectionTestUtils.invokeMethod(
                service,
                "canAdvanceRenderRecord",
                currentStatus,
                nextStatus
        );
        return Boolean.TRUE.equals(result);
    }
}
