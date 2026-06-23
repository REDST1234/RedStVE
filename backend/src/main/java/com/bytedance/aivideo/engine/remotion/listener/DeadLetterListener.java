package com.bytedance.aivideo.engine.remotion.listener;

import com.bytedance.aivideo.account.service.AccountService;
import com.bytedance.aivideo.config.RabbitMQConfig;
import com.bytedance.aivideo.engine.remotion.dto.RenderTaskRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterListener {

    private final AccountService accountService;

    /**
     * 监听死信队列：当渲染任务过期（60s 未处理）或者被 Node.js 显式拒绝时，触发此方法进行退钱。
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_RENDER_DLQ)
    public void processDeadLetter(RenderTaskRequest failedRequest) {
        log.warn("🚨 [死信告警] 收到过期或被拒绝的渲染任务！准备执行退款补偿...");
        log.warn("任务信息: taskId={}, userId={}, cost={}", 
                failedRequest.getTaskId(), failedRequest.getUserId(), failedRequest.getCost());

        try {
            // 幂等凭证：使用 taskId 作为业务凭证，防止同一个死信被多次退钱
            String bizRef = "DLX_REFUND_" + failedRequest.getTaskId();
            
            // 发起退款
            accountService.refundPoints(
                failedRequest.getUserId(),
                failedRequest.getCost(),
                bizRef,
                "死信队列兜底：渲染节点宕机或处理超时，积分自动退还"
            );
            
            log.info("✅ [补偿成功] 已成功为用户 {} 退还 {} 积分。", failedRequest.getUserId(), failedRequest.getCost());
            
            // 此处可以拓展逻辑：通过 VideoOrchestrationService 将数据库中任务状态标记为 FAILED
            
        } catch (Exception e) {
            log.error("❌ [补偿失败] 退还积分时发生严重异常，需人工介入！", e);
            // 注意：既然已经是死信了，就不再 throw 异常，否则它要么被丢弃，要么在死信队列里循环。
            // 真实的工业界做法是记录一条报警日志发送给运维，或者存入一张 manual_compensation_table。
        }
    }
}
