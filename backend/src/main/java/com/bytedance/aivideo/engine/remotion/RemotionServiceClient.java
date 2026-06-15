package com.bytedance.aivideo.engine.remotion;

import com.bytedance.aivideo.creation.dto.remotion.CompositionScript;
import com.bytedance.aivideo.engine.remotion.dto.RenderResponse;
import com.bytedance.aivideo.engine.remotion.dto.RenderTaskRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RemotionServiceClient {

    private final RemotionFeignClient remotionFeignClient;

    public RemotionServiceClient(RemotionFeignClient remotionFeignClient) {
        this.remotionFeignClient = remotionFeignClient;
    }

    /**
     * 提交渲染任务给 Remotion 微服务 (现在通过 Nacos + OpenFeign 动态寻址调用)
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
            // 直接调用 Feign 接口，底层会自动去 Nacos 查找 remotion-service 的 IP 并发起请求
            return remotionFeignClient.submitRenderTask(request);
        } catch (Exception e) {
            log.error("Failed to submit render task to Remotion microservice: {}", taskId, e);
            RenderResponse errorResponse = new RenderResponse();
            errorResponse.setTaskId(taskId);
            errorResponse.setStatus("FAILED");
            errorResponse.setError("Microservice Call Failed: " + e.getMessage());
            return errorResponse;
        }
    }
}
