package com.bytedance.aivideo.controller;

import com.bytedance.aivideo.account.service.AccountService;
import com.bytedance.aivideo.creation.dto.remotion.CompositionScript;
import com.bytedance.aivideo.engine.remotion.RemotionServiceClient;
import com.bytedance.aivideo.engine.remotion.dto.RenderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 用于测试微服务改造后的 Remotion 调用，避免走大模型消耗 API Key
 */
@Slf4j
@RestController
@RequestMapping("/api/test/remotion")
@RequiredArgsConstructor
public class RemotionTestController {

    private final RemotionServiceClient remotionServiceClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AccountService accountService;

    /**
     * 读取 Redis 中已有的渲染 JSON，直接发给 Node.js 微服务进行渲染
     *
     * @param redisKey Redis 中的 key 名字
     * @return 渲染微服务的响应结果
     */
    @PostMapping("/render-from-redis")
    public RenderResponse renderFromRedis(@RequestParam String redisKey) {
        try {
            // 生成一个随机的任务 ID
            String taskId = "test-render-" + UUID.randomUUID().toString().substring(0, 8);

            // 1. 从 Redis 读取之前存好的完整 JSON 字符串
            String jsonStr = stringRedisTemplate.opsForValue().get(redisKey);
            if (jsonStr == null || jsonStr.isBlank()) {
                RenderResponse err = new RenderResponse();
                err.setStatus("FAILED");
                err.setError("找不到 Redis Key 或者内容为空: " + redisKey);
                return err;
            }

            // 2. 将 JSON 字符串反序列化为 Java 对象
            CompositionScript script = objectMapper.readValue(jsonStr, CompositionScript.class);

            // 3. 通过微服务 (Feign) 把对象发送给 Node.js 端
            return remotionServiceClient.submitRenderTask(taskId, script);
            
        } catch (Exception e) {
            RenderResponse err = new RenderResponse();
            err.setStatus("FAILED");
            err.setError("测试接口执行异常: " + e.getMessage());
            return err;
        }
    }

    /**
     * 【一键演示：分布式补偿事务与死信队列】
     * 1. 扣除积分
     * 2. 发送一个必定会失败（格式错误）的渲染任务到 RabbitMQ
     * 3. 观察 Node.js 拒收，引发死信队列自动退还积分
     */
    @PostMapping("/trigger-dlq")
    public String triggerDeadLetterQueueCompensation() {
        String taskId = "DLQ-TEST-" + UUID.randomUUID().toString().substring(0, 8);
        String userId = "test_user_001";
        
        log.info("======== 开始死信队列演示 ========");

        // 1. 强行扣费 10 积分
        accountService.deductPoints(userId, 10, "DLQ_DEMO_" + taskId, "发起测试渲染任务");
        log.info("👉 第一步：账户服务扣费 10 积分成功");

        // 2. 故意构造一个完全不合法的空白剧本
        CompositionScript badScript = new CompositionScript(); 
        
        // 3. 发往微服务
        remotionServiceClient.submitRenderTask(taskId, badScript);
        log.info("👉 第二步：已将毒药任务发送至 RabbitMQ，等待 Node.js 崩溃拒收并触发 DLQ...");

        return "已触发 DLQ 流程，请观察 IDEA 和 Node.js 控制台的日志输出！";
    }
}
