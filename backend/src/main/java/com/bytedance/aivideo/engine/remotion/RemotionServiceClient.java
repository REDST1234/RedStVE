package com.bytedance.aivideo.engine.remotion;

import com.bytedance.aivideo.account.service.AccountService;
import com.bytedance.aivideo.config.RabbitMQConfig;
import com.bytedance.aivideo.creation.dto.remotion.CompositionScript;
import com.bytedance.aivideo.engine.remotion.dto.RenderResponse;
import com.bytedance.aivideo.engine.remotion.dto.RenderTaskRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemotionServiceClient {

    private final RabbitTemplate rabbitTemplate;
    private final AccountService accountService;

    /**
     * 提交渲染任务给 Remotion 微服务 (现在通过 RabbitMQ 异步发送)
     *
     * @param taskId 任务ID
     * @param script 编排 JSON (CompositionScript)
     * @return 提交响应
     */
    public RenderResponse submitRenderTask(String taskId, CompositionScript script) {
        // 在真实的微服务中，userId 从 SecurityContextHolder (Spring Security) 获取
        // 这里为了 TDD 连贯性，我们暂时写死，因为前端暂未传递
        String currentUserId = "test_user_001";
        int currentCost = 10;

        // 1. 发件前，我们应当在外部包裹 @Transactional 先行扣费
        // （详情见我们在架构设计中讲到的“本地消息表/柔性事务”概念）

        RenderTaskRequest request = new RenderTaskRequest();
        request.setTaskId(taskId);
        request.setUserId(currentUserId);
        request.setCost(currentCost);
        request.setCompositionScript(script);

        try {
            log.info("Sending render task {} to RabbitMQ...", taskId);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_REMOTION, RabbitMQConfig.ROUTING_KEY_RENDER, request);
            
            RenderResponse response = new RenderResponse();
            response.setTaskId(taskId);
            response.setStatus("QUEUED"); // 状态改为队列中
            return response;
        } catch (AmqpException e) {
            // 混沌工程增强 3：网络断连/MQ宕机 兜底机制
            // 此时消息根本没发出去，死信队列是帮不上忙的，必须立刻原地退钱！
            log.error("💥 [致命错误] 发送渲染任务至 RabbitMQ 失败！网络异常或 Broker 宕机。taskId={}", taskId, e);
            
            String bizRef = "SYNC_REFUND_" + taskId;
            log.info("💰 [同步补偿] 触发发件箱兜底，立即退还用户 {} 渲染积分...", currentUserId);
            accountService.refundPoints(currentUserId, currentCost, bizRef, "MQ发件箱异常，同步退还积分");
            log.info("✅ [同步补偿] 积分退还成功！");

            RenderResponse errorResponse = new RenderResponse();
            errorResponse.setTaskId(taskId);
            errorResponse.setStatus("FAILED");
            errorResponse.setError("System is extremely busy (MQ Down). Points refunded.");
            return errorResponse;
            
        } catch (Exception e) {
            log.error("Failed to send render task to RabbitMQ: {}", taskId, e);
            RenderResponse errorResponse = new RenderResponse();
            errorResponse.setTaskId(taskId);
            errorResponse.setStatus("FAILED");
            errorResponse.setError("Unknown Error: " + e.getMessage());
            return errorResponse;
        }
    }
}
