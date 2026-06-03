package com.bytedance.aivideo.engine.remotion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bytedance.aivideo.creation.dto.remotion.CompositionScript;
import com.bytedance.aivideo.engine.remotion.dto.RenderResponse;
import com.bytedance.aivideo.engine.remotion.dto.RenderTaskRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class RemotionServiceClient {

    @Value("${remotion.service.url:http://localhost:3001}")
    private String remotionServiceUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper requestObjectMapper;

    public RemotionServiceClient() {
        this.restTemplate = new RestTemplate();
        this.requestObjectMapper = new ObjectMapper();
        this.requestObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * 提交渲染任务给 Remotion 微服务
     *
     * @param taskId 任务ID
     * @param script 编排 JSON (CompositionScript)
     * @return 提交响应
     */
    public RenderResponse submitRenderTask(String taskId, CompositionScript script) {
        String url = remotionServiceUrl + "/render";
        
        RenderTaskRequest request = new RenderTaskRequest();
        request.setTaskId(taskId);
        request.setCompositionScript(script);

        try {
            String payload = requestObjectMapper.writeValueAsString(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<RenderResponse> response = restTemplate.postForEntity(url, entity, RenderResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to submit render task to Remotion service: {}", taskId, e);
            RenderResponse errorResponse = new RenderResponse();
            errorResponse.setTaskId(taskId);
            errorResponse.setStatus("FAILED");
            errorResponse.setError(e.getMessage());
            return errorResponse;
        }
    }
}
