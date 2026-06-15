package com.bytedance.aivideo.controller;

import com.bytedance.aivideo.creation.dto.remotion.CompositionScript;
import com.bytedance.aivideo.engine.remotion.RemotionServiceClient;
import com.bytedance.aivideo.engine.remotion.dto.RenderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 用于测试微服务改造后的 Remotion 调用，避免走大模型消耗 API Key
 */
@RestController
@RequestMapping("/api/test/remotion")
public class RemotionTestController {

    private final RemotionServiceClient remotionServiceClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RemotionTestController(RemotionServiceClient remotionServiceClient, 
                                  StringRedisTemplate stringRedisTemplate,
                                  ObjectMapper objectMapper) {
        this.remotionServiceClient = remotionServiceClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

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
}
