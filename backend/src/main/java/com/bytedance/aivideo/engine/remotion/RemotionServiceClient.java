package com.bytedance.aivideo.engine.remotion;

import com.bytedance.aivideo.config.RabbitMQConfig;
import com.bytedance.aivideo.creation.dto.remotion.CompositionScript;
import com.bytedance.aivideo.engine.remotion.dto.RenderResponse;
import com.bytedance.aivideo.engine.remotion.dto.RenderTaskRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RemotionServiceClient {

    private final RabbitTemplate rabbitTemplate;

    public RemotionServiceClient(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 提交渲染任务给 Remotion 微服务 (现在通过 RabbitMQ 异步发送)
     *
     * @param taskId 任务ID
     * @param script 编排 JSON (CompositionScript)
     * @return 提交响应
     */
    public RenderResponse submitRenderTask(String taskId, CompositionScript script) {
        RenderTaskRequest request = new RenderTaskRequest();
        request.setTaskId(taskId);
        request.setCompositionScript(script);

        try {
            log.info("Sending render task {} to RabbitMQ...", taskId);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_REMOTION, RabbitMQConfig.ROUTING_KEY_RENDER, request);
            
            RenderResponse response = new RenderResponse();
            response.setTaskId(taskId);
            response.setStatus("QUEUED"); // 状态改为队列中
            return response;
        } catch (Exception e) {
            log.error("Failed to send render task to RabbitMQ: {}", taskId, e);
            RenderResponse errorResponse = new RenderResponse();
            errorResponse.setTaskId(taskId);
            errorResponse.setStatus("FAILED");
            errorResponse.setError("MQ Call Failed: " + e.getMessage());
            return errorResponse;
        }
    }
}
