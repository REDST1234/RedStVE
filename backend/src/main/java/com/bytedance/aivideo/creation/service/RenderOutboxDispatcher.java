package com.bytedance.aivideo.creation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bytedance.aivideo.config.RabbitMQConfig;
import com.bytedance.aivideo.creation.entity.CreationProjectEntity;
import com.bytedance.aivideo.creation.entity.OutboxMessageEntity;
import com.bytedance.aivideo.creation.entity.RenderRecordEntity;
import com.bytedance.aivideo.creation.mapper.CreationProjectMapper;
import com.bytedance.aivideo.creation.mapper.OutboxMessageMapper;
import com.bytedance.aivideo.creation.mapper.RenderRecordMapper;
import com.bytedance.aivideo.engine.remotion.dto.RenderTaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class RenderOutboxDispatcher {

    private static final String OUTBOX_MESSAGE_TYPE_RENDER_SUBMIT = "RENDER_SUBMIT";
    private static final String OUTBOX_STATUS_PENDING = "PENDING";
    private static final String OUTBOX_STATUS_SENDING = "SENDING";
    private static final String OUTBOX_STATUS_SENT = "SENT";
    private static final String OUTBOX_STATUS_DEAD = "DEAD";

    private static final String RENDER_RECORD_STATUS_CREATED = "CREATED";
    private static final String RENDER_RECORD_STATUS_QUEUED = "QUEUED";
    private static final String RENDER_RECORD_STATUS_RENDERING = "RENDERING";
    private static final String RENDER_RECORD_STATUS_DONE = "DONE";
    private static final String RENDER_RECORD_STATUS_FAILED = "FAILED";

    private final OutboxMessageMapper outboxMessageMapper;
    private final RenderRecordMapper renderRecordMapper;
    private final CreationProjectMapper creationProjectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${render.outbox.dispatcher.batch-size:20}")
    private int batchSize;

    @Value("${render.outbox.dispatcher.max-retry-count:4}")
    private int maxRetryCount;

    @Value("${render.outbox.dispatcher.sending-timeout-seconds:60}")
    private long sendingTimeoutSeconds;

    @Value("${render.runtime.queue-timeout-seconds:120}")
    private long queueTimeoutSeconds;

    @Value("${render.runtime.rendering-timeout-seconds:1800}")
    private long renderingTimeoutSeconds;

    @Scheduled(fixedDelayString = "${render.outbox.dispatcher.fixed-delay-ms:5000}")
    public void dispatchPendingMessages() {
        List<OutboxMessageEntity> pendingMessages = outboxMessageMapper.selectList(
                new LambdaQueryWrapper<OutboxMessageEntity>()
                        .eq(OutboxMessageEntity::getMessageType, OUTBOX_MESSAGE_TYPE_RENDER_SUBMIT)
                        .eq(OutboxMessageEntity::getStatus, OUTBOX_STATUS_PENDING)
                        .le(OutboxMessageEntity::getNextRetryAt, LocalDateTime.now())
                        .orderByAsc(OutboxMessageEntity::getCreatedAt)
                        .last("LIMIT " + Math.max(batchSize, 1))
        );
        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return;
        }

        for (OutboxMessageEntity pendingMessage : pendingMessages) {
            if (pendingMessage == null || pendingMessage.getId() == null) {
                continue;
            }
            if (!claimSendingRight(pendingMessage.getId())) {
                continue;
            }
            processClaimedMessage(pendingMessage.getId());
        }
    }

    @Scheduled(fixedDelayString = "${render.outbox.dispatcher.recover-fixed-delay-ms:30000}")
    public void recoverStaleSendingMessages() {
        LocalDateTime expireBefore = LocalDateTime.now().minusSeconds(Math.max(sendingTimeoutSeconds, 1));
        List<OutboxMessageEntity> staleMessages = outboxMessageMapper.selectList(
                new LambdaQueryWrapper<OutboxMessageEntity>()
                        .eq(OutboxMessageEntity::getMessageType, OUTBOX_MESSAGE_TYPE_RENDER_SUBMIT)
                        .eq(OutboxMessageEntity::getStatus, OUTBOX_STATUS_SENDING)
                        .lt(OutboxMessageEntity::getUpdatedAt, expireBefore)
                        .last("LIMIT " + Math.max(batchSize, 1))
        );
        if (staleMessages == null || staleMessages.isEmpty()) {
            return;
        }

        for (OutboxMessageEntity staleMessage : staleMessages) {
            if (staleMessage == null || staleMessage.getId() == null) {
                continue;
            }
            staleMessage.setStatus(OUTBOX_STATUS_PENDING);
            staleMessage.setNextRetryAt(LocalDateTime.now());
            staleMessage.setLastError("dispatcher timeout recovered");
            outboxMessageMapper.updateById(staleMessage);
            log.warn("Recovered stale outbox message from SENDING to PENDING: messageId={}, bizKey={}",
                    staleMessage.getMessageId(), staleMessage.getBizKey());
        }
    }

    @Scheduled(fixedDelayString = "${render.runtime.timeout-check-fixed-delay-ms:30000}")
    public void failTimedOutRenderRecords() {
        recoverTimedOutQueuedRecords();
        recoverTimedOutRenderingRecords();
    }

    private boolean claimSendingRight(Long outboxId) {
        UpdateWrapper<OutboxMessageEntity> claimWrapper = new UpdateWrapper<>();
        claimWrapper.eq("id", outboxId)
                .eq("status", OUTBOX_STATUS_PENDING)
                .set("status", OUTBOX_STATUS_SENDING)
                .set("updated_at", LocalDateTime.now());
        return outboxMessageMapper.update(null, claimWrapper) > 0;
    }

    private void processClaimedMessage(Long outboxId) {
        OutboxMessageEntity message = outboxMessageMapper.selectById(outboxId);
        if (message == null) {
            return;
        }

        try {
            RenderTaskRequest request = objectMapper.readValue(message.getPayload(), RenderTaskRequest.class);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_REMOTION,
                    RabbitMQConfig.ROUTING_KEY_RENDER,
                    request
            );
            markSendSuccess(message);
        } catch (AmqpException e) {
            handleSendFailure(message, e);
        } catch (Exception e) {
            handleSendFailure(message, e);
        }
    }

    private void markSendSuccess(OutboxMessageEntity message) {
        message.setStatus(OUTBOX_STATUS_SENT);
        message.setLastError(null);
        outboxMessageMapper.updateById(message);

        renderRecordMapper.update(
                null,
                new LambdaUpdateWrapper<RenderRecordEntity>()
                        .eq(RenderRecordEntity::getRenderId, message.getBizKey())
                        .eq(RenderRecordEntity::getStatus, RENDER_RECORD_STATUS_CREATED)
                        .set(RenderRecordEntity::getStatus, RENDER_RECORD_STATUS_QUEUED)
        );

        CreationProjectEntity project = creationProjectMapper.selectOne(
                new LambdaQueryWrapper<CreationProjectEntity>()
                        .eq(CreationProjectEntity::getLatestRenderId, message.getBizKey())
                        .last("LIMIT 1")
        );
        if (project != null) {
            project.setStatus(RENDER_RECORD_STATUS_QUEUED);
            creationProjectMapper.updateById(project);
        }

        log.info("Outbox message sent successfully: messageId={}, bizKey={}",
                message.getMessageId(), message.getBizKey());
    }

    private void handleSendFailure(OutboxMessageEntity message, Exception exception) {
        int nextRetryCount = (message.getRetryCount() == null ? 0 : message.getRetryCount()) + 1;
        String errorMessage = trimErrorMessage(exception);

        if (nextRetryCount > Math.max(maxRetryCount, 0)) {
            message.setStatus(OUTBOX_STATUS_DEAD);
            message.setRetryCount(nextRetryCount);
            message.setLastError(errorMessage);
            outboxMessageMapper.updateById(message);
            markRenderChainFailed(message.getBizKey());
            log.error("Outbox message entered DEAD state after retries exhausted: messageId={}, bizKey={}, error={}",
                    message.getMessageId(), message.getBizKey(), errorMessage);
            return;
        }

        message.setStatus(OUTBOX_STATUS_PENDING);
        message.setRetryCount(nextRetryCount);
        message.setNextRetryAt(LocalDateTime.now().plusSeconds(calculateRetryDelaySeconds(nextRetryCount)));
        message.setLastError(errorMessage);
        outboxMessageMapper.updateById(message);

        log.warn("Outbox message send failed and will retry: messageId={}, bizKey={}, retryCount={}, error={}",
                message.getMessageId(), message.getBizKey(), nextRetryCount, errorMessage);
    }

    private long calculateRetryDelaySeconds(int retryCount) {
        return switch (retryCount) {
            case 1 -> 10L + ThreadLocalRandom.current().nextLong(0, 4);
            case 2 -> 20L + ThreadLocalRandom.current().nextLong(0, 6);
            case 3 -> 30L + ThreadLocalRandom.current().nextLong(0, 9);
            default -> 60L + ThreadLocalRandom.current().nextLong(0, 11);
        };
    }

    private void markRenderChainFailed(String renderId) {
        renderRecordMapper.update(
                null,
                new LambdaUpdateWrapper<RenderRecordEntity>()
                        .eq(RenderRecordEntity::getRenderId, renderId)
                        .eq(RenderRecordEntity::getStatus, RENDER_RECORD_STATUS_CREATED)
                        .set(RenderRecordEntity::getStatus, RENDER_RECORD_STATUS_FAILED)
        );

        CreationProjectEntity project = creationProjectMapper.selectOne(
                new LambdaQueryWrapper<CreationProjectEntity>()
                        .eq(CreationProjectEntity::getLatestRenderId, renderId)
                        .last("LIMIT 1")
        );
        if (project != null) {
            project.setStatus(RENDER_RECORD_STATUS_FAILED);
            creationProjectMapper.updateById(project);
        }
    }

    private void recoverTimedOutQueuedRecords() {
        LocalDateTime expireBefore = LocalDateTime.now().minusSeconds(Math.max(queueTimeoutSeconds, 1));
        List<RenderRecordEntity> timedOutQueued = renderRecordMapper.selectList(
                new LambdaQueryWrapper<RenderRecordEntity>()
                        .eq(RenderRecordEntity::getStatus, RENDER_RECORD_STATUS_QUEUED)
                        .lt(RenderRecordEntity::getUpdatedAt, expireBefore)
                        .last("LIMIT " + Math.max(batchSize, 1))
        );
        for (RenderRecordEntity record : timedOutQueued) {
            failRenderRecord(record, "queue timeout");
        }
    }

    private void recoverTimedOutRenderingRecords() {
        LocalDateTime expireBefore = LocalDateTime.now().minusSeconds(Math.max(renderingTimeoutSeconds, 1));
        List<RenderRecordEntity> timedOutRendering = renderRecordMapper.selectList(
                new LambdaQueryWrapper<RenderRecordEntity>()
                        .eq(RenderRecordEntity::getStatus, RENDER_RECORD_STATUS_RENDERING)
                        .lt(RenderRecordEntity::getUpdatedAt, expireBefore)
                        .last("LIMIT " + Math.max(batchSize, 1))
        );
        for (RenderRecordEntity record : timedOutRendering) {
            failRenderRecord(record, "rendering timeout");
        }
    }

    private void failRenderRecord(RenderRecordEntity record, String reason) {
        if (record == null || record.getRenderId() == null || record.getRenderId().isBlank()) {
            return;
        }
        boolean updated = renderRecordMapper.update(
                null,
                new LambdaUpdateWrapper<RenderRecordEntity>()
                        .eq(RenderRecordEntity::getId, record.getId())
                        .eq(RenderRecordEntity::getStatus, record.getStatus())
                        .set(RenderRecordEntity::getStatus, RENDER_RECORD_STATUS_FAILED)
        ) > 0;
        if (!updated) {
            return;
        }

        CreationProjectEntity project = creationProjectMapper.selectOne(
                new LambdaQueryWrapper<CreationProjectEntity>()
                        .eq(CreationProjectEntity::getLatestRenderId, record.getRenderId())
                        .last("LIMIT 1")
        );
        if (project != null && !RENDER_RECORD_STATUS_DONE.equals(project.getStatus())) {
            project.setStatus(RENDER_RECORD_STATUS_FAILED);
            creationProjectMapper.updateById(project);
        }
        log.warn("Render record timed out and was marked FAILED: renderId={}, previousStatus={}, reason={}",
                record.getRenderId(), record.getStatus(), reason);
    }

    private String trimErrorMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception == null ? "unknown error" : exception.getClass().getSimpleName();
        }
        String raw = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return raw.length() > 1024 ? raw.substring(0, 1024) : raw;
    }
}
