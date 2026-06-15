package com.bytedance.aivideo.engine.remotion;

import com.bytedance.aivideo.engine.remotion.dto.RenderResponse;
import com.bytedance.aivideo.engine.remotion.dto.RenderTaskRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign Client for calling the Remotion Node.js Microservice.
 * Name must match the SERVICE_NAME registered in Nacos.
 */
@FeignClient(name = "remotion-service")
public interface RemotionFeignClient {

    @PostMapping("/render")
    RenderResponse submitRenderTask(@RequestBody RenderTaskRequest request);

}
