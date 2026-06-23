package com.bytedance.aivideo.creation.listener;

import com.bytedance.aivideo.account.service.AccountService;
import com.bytedance.aivideo.config.RabbitMQConfig;
import com.bytedance.aivideo.engine.remotion.dto.RenderTaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RenderDeadLetterListener {

    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    /**
     * 监听死信队列
     * 当 Node.js 渲染集群彻底崩溃、或渲染代码存在语法错误导致执行失败且被 nack 时，
     * 消息会自动掉入这个死信队列。
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_RENDER_DLQ)
    public void onRenderDeadLetterMessage(Message message) {
        try {
            // 1. 反序列化消息内容
            String jsonPayload = new String(message.getBody());
            RenderTaskRequest request = objectMapper.readValue(jsonPayload, RenderTaskRequest.class);
            
            String taskId = request.getTaskId();
            log.error("💀 [死信队列] 收到渲染失败的死信任务: taskId={}", taskId);

            // 2. 根据 taskId 查询出是哪个用户发起的任务
            // TODO: 这里需要查询数据库 (比如 CreationProject 表或 Task 表) 获取真实 userId
            // 临时写死为我们在测试中使用的用户
            String userId = "test_user_001";
            
            // 3. 核心业务操作：分布式事务补偿 —— 退还积分！
            log.info("💰 [补偿事务] 正在为用户 {} 退还渲染消耗的积分...", userId);
            
            String bizRef = "REFUND_" + taskId;
            accountService.refundPoints(userId, 10, bizRef, "渲染失败，死信队列自动退还积分");
            
            // 4. Spring AMQP 默认是 AUTO Ack 模式。
            // 只要这个方法正常执行结束，没有抛出异常，Spring 就会自动向 RabbitMQ 发送 ACK，把死信彻底从队列删除。
            log.info("✅ [死信队列] 补偿事务执行完毕，积分已退还。");
            
        } catch (Exception e) {
            log.error("❌ 处理死信消息失败，准备无限重试...", e);
            // 抛出异常后，Spring 会捕获并自动发送 Nack(requeue=true)，让这条消息留在死信队列里不断重试
            throw new RuntimeException("补偿事务失败，要求 Spring 重新入队", e);
        }
    }
}
