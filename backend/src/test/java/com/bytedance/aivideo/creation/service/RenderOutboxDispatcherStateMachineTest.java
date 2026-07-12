package com.bytedance.aivideo.creation.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bytedance.aivideo.config.RabbitMQConfig;
import com.bytedance.aivideo.creation.entity.CreationProjectEntity;
import com.bytedance.aivideo.creation.entity.OutboxMessageEntity;
import com.bytedance.aivideo.creation.entity.RenderRecordEntity;
import com.bytedance.aivideo.creation.mapper.CreationProjectMapper;
import com.bytedance.aivideo.creation.mapper.OutboxMessageMapper;
import com.bytedance.aivideo.creation.mapper.RenderRecordMapper;
import com.bytedance.aivideo.engine.remotion.dto.RenderTaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenderOutboxDispatcherStateMachineTest {

    @Mock
    private OutboxMessageMapper outboxMessageMapper;

    @Mock
    private RenderRecordMapper renderRecordMapper;

    @Mock
    private CreationProjectMapper creationProjectMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RenderOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        initTableInfo(OutboxMessageEntity.class);
        initTableInfo(RenderRecordEntity.class);
        initTableInfo(CreationProjectEntity.class);

        dispatcher = new RenderOutboxDispatcher(
                outboxMessageMapper,
                renderRecordMapper,
                creationProjectMapper,
                rabbitTemplate,
                objectMapper
        );
        ReflectionTestUtils.setField(dispatcher, "batchSize", 10);
        ReflectionTestUtils.setField(dispatcher, "maxRetryCount", 4);
        ReflectionTestUtils.setField(dispatcher, "sendingTimeoutSeconds", 60L);
        ReflectionTestUtils.setField(dispatcher, "queueTimeoutSeconds", 120L);
        ReflectionTestUtils.setField(dispatcher, "renderingTimeoutSeconds", 1800L);
    }

    @Test
    void shouldAdvanceRenderChainToQueuedAfterOutboxSendSuccess() throws Exception {
        OutboxMessageEntity pendingMessage = new OutboxMessageEntity();
        pendingMessage.setId(1L);
        pendingMessage.setMessageId("msg-1");
        pendingMessage.setMessageType("RENDER_SUBMIT");
        pendingMessage.setBizKey("render-1");
        pendingMessage.setStatus("PENDING");
        pendingMessage.setPayload(renderTaskPayload("render-1"));
        pendingMessage.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        pendingMessage.setNextRetryAt(LocalDateTime.now().minusSeconds(5));

        CreationProjectEntity project = new CreationProjectEntity();
        project.setId(10L);
        project.setLatestRenderId("render-1");
        project.setStatus("CREATED");

        when(outboxMessageMapper.selectList(any())).thenReturn(List.of(pendingMessage));
        when(outboxMessageMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(outboxMessageMapper.selectById(1L)).thenReturn(pendingMessage);
        when(creationProjectMapper.selectOne(any())).thenReturn(project);

        dispatcher.dispatchPendingMessages();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_REMOTION),
                eq(RabbitMQConfig.ROUTING_KEY_RENDER),
                any(RenderTaskRequest.class)
        );

        ArgumentCaptor<OutboxMessageEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxMessageEntity.class);
        verify(outboxMessageMapper).updateById(outboxCaptor.capture());
        assertEquals("SENT", outboxCaptor.getValue().getStatus());
        assertNull(outboxCaptor.getValue().getLastError());

        verify(renderRecordMapper).update(isNull(), any(LambdaUpdateWrapper.class));

        ArgumentCaptor<CreationProjectEntity> projectCaptor = ArgumentCaptor.forClass(CreationProjectEntity.class);
        verify(creationProjectMapper).updateById(projectCaptor.capture());
        assertEquals("QUEUED", projectCaptor.getValue().getStatus());
    }

    @Test
    void shouldMarkTimedOutRecordsFailedAndSyncLatestProject() {
        RenderRecordEntity queuedRecord = new RenderRecordEntity();
        queuedRecord.setId(11L);
        queuedRecord.setRenderId("render-timeout");
        queuedRecord.setStatus("QUEUED");
        queuedRecord.setUpdatedAt(LocalDateTime.now().minusMinutes(10));

        CreationProjectEntity project = new CreationProjectEntity();
        project.setId(21L);
        project.setLatestRenderId("render-timeout");
        project.setStatus("QUEUED");

        when(renderRecordMapper.selectList(any())).thenReturn(List.of(queuedRecord), List.of());
        when(renderRecordMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(creationProjectMapper.selectOne(any())).thenReturn(project);

        dispatcher.failTimedOutRenderRecords();

        verify(renderRecordMapper).update(isNull(), any(LambdaUpdateWrapper.class));

        ArgumentCaptor<CreationProjectEntity> projectCaptor = ArgumentCaptor.forClass(CreationProjectEntity.class);
        verify(creationProjectMapper).updateById(projectCaptor.capture());
        assertEquals("FAILED", projectCaptor.getValue().getStatus());
    }

    @Test
    void shouldNotOverwriteDoneProjectWhenTimedOutRecordIsRecovered() {
        RenderRecordEntity renderingRecord = new RenderRecordEntity();
        renderingRecord.setId(12L);
        renderingRecord.setRenderId("render-done-project");
        renderingRecord.setStatus("RENDERING");
        renderingRecord.setUpdatedAt(LocalDateTime.now().minusHours(2));

        CreationProjectEntity doneProject = new CreationProjectEntity();
        doneProject.setId(22L);
        doneProject.setLatestRenderId("render-done-project");
        doneProject.setStatus("DONE");

        when(renderRecordMapper.selectList(any())).thenReturn(List.of(), List.of(renderingRecord));
        when(renderRecordMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(creationProjectMapper.selectOne(any())).thenReturn(doneProject);

        dispatcher.failTimedOutRenderRecords();

        verify(renderRecordMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(creationProjectMapper, never()).updateById(any(CreationProjectEntity.class));
    }

    private String renderTaskPayload(String renderId) throws Exception {
        RenderTaskRequest request = new RenderTaskRequest();
        request.setTaskId(renderId);
        request.setUserId("test_user_001");
        request.setCost(10);
        return objectMapper.writeValueAsString(request);
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
