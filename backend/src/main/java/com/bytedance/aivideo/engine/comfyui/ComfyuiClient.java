package com.bytedance.aivideo.engine.comfyui;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.ComfyuiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * ComfyUI HTTP 客户端，封装 upload / prompt / history / view 四个核心接口。
 */
@Component
@Slf4j
public class ComfyuiClient {

    private final HttpClient httpClient;
    private final ComfyuiProperties properties;
    private final ObjectMapper objectMapper;

    public ComfyuiClient(ComfyuiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds())))
                .build();
    }

    /**
     * 上传图片到 ComfyUI。
     *
     * @return { name, subfolder, type } 供 workflow 中 LoadImage 节点引用
     */
    public Map<String, String> uploadImage(Path localFile) {
        String boundary = "comfyuiUpload" + System.currentTimeMillis();
        String url = normalizeUrl("/upload/image");

        try {
            byte[] fileBytes = Files.readAllBytes(localFile);
            String filename = localFile.getFileName().toString();
            byte[] body = buildMultipartBody(boundary, filename, fileBytes);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds())))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensure2xx(response, "ComfyUI upload");

            JsonNode root = objectMapper.readTree(response.body());
            String name = root.path("name").asText();
            String subfolder = root.path("subfolder").asText("");
            String type = root.path("type").asText("input");

            if (name.isEmpty()) {
                throw new BizException(ErrorCode.ARK_API_ERROR, "ComfyUI upload 返回空 name");
            }
            log.info("comfyui upload ok: file={}, name={}", filename, name);
            return Map.of("name", name, "subfolder", subfolder, "type", type);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.ARK_API_ERROR, "ComfyUI upload 失败: " + ex.toString());
        }
    }

    /**
     * 提交工作流并返回 prompt_id。
     * ComfyUI /prompt 要求 {"prompt": <workflow nodes>, "client_id": "..."}
     */
    public String submitWorkflow(String workflowJson) {
        String url = normalizeUrl("/prompt");

        try {
            JsonNode promptNodes = objectMapper.readTree(workflowJson);
            String body = objectMapper.writeValueAsString(Map.of(
                    "prompt", promptNodes,
                    "client_id", "ai-video-comfyui"
            ));

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensure2xx(response, "ComfyUI prompt");

            JsonNode root = objectMapper.readTree(response.body());
            String promptId = root.path("prompt_id").asText();
            if (promptId.isEmpty()) {
                String error = root.path("error").asText("");
                throw new BizException(ErrorCode.ARK_API_ERROR,
                        "ComfyUI prompt 返回空 prompt_id, error=" + error);
            }
            log.info("comfyui prompt submitted: promptId={}", promptId);
            return promptId;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.ARK_API_ERROR, "ComfyUI prompt 提交失败: " + ex.toString());
        }
    }

    /**
     * 轮询直到工作流执行完成，返回 outputs 节点（含 output 图片文件名）。
     */
    public JsonNode pollHistory(String promptId) {
        String url = normalizeUrl("/history/" + promptId);
        long deadline = System.currentTimeMillis() + properties.getPollTimeoutSeconds() * 1000L;

        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds())))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) {
                    // not yet available, retry
                    Thread.sleep(properties.getPollIntervalMs());
                    continue;
                }
                ensure2xx(response, "ComfyUI history");

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode entry = root.path(promptId);
                if (entry.isMissingNode() || entry.isNull()) {
                    Thread.sleep(properties.getPollIntervalMs());
                    continue;
                }

                JsonNode outputs = entry.path("outputs");
                if (!outputs.isMissingNode() && !outputs.isNull() && !outputs.isEmpty()) {
                    log.info("comfyui history completed: promptId={}", promptId);
                    return outputs;
                }

                JsonNode status = entry.path("status");
                if (status.has("completed") && status.path("completed").asBoolean(false)) {
                    // completed but no outputs — may have errored
                    String statusStr = status.path("status_str").asText();
                    throw new BizException(ErrorCode.ARK_API_ERROR,
                            "ComfyUI 执行完成但无 outputs: " + statusStr);
                }
            } catch (BizException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BizException(ErrorCode.ARK_API_ERROR, "ComfyUI 轮询被中断");
            } catch (Exception ex) {
                log.warn("comfyui history poll error: {}", ex.getMessage());
                // retry on transient errors
            }

            try {
                Thread.sleep(properties.getPollIntervalMs());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BizException(ErrorCode.ARK_API_ERROR, "ComfyUI 轮询被中断");
            }
        }
        throw new BizException(ErrorCode.ARK_API_ERROR,
                "ComfyUI 轮询超时: promptId=" + promptId + ", timeout=" + properties.getPollTimeoutSeconds() + "s");
    }

    /**
     * 下载生成结果图片到本地。
     */
    public void downloadOutput(String filename, String subfolder, String type, Path outputFile) {
        String url = normalizeUrl("/view?filename=" + urlEncode(filename)
                + "&subfolder=" + (subfolder != null ? urlEncode(subfolder) : "")
                + "&type=" + (type != null ? urlEncode(type) : "output"));

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds())))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            ensure2xx(response, "ComfyUI view");

            Files.write(outputFile, response.body());
            log.info("comfyui download ok: {} -> {} ({} bytes)", url, outputFile, response.body().length);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.ARK_API_ERROR, "ComfyUI 下载输出失败: " + ex.toString());
        }
    }

    private byte[] buildMultipartBody(String boundary, String filename, byte[] fileBytes) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"image\"; filename=\"").append(filename).append("\"\r\n");
        sb.append("Content-Type: application/octet-stream\r\n\r\n");

        byte[] header = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] body = new byte[header.length + fileBytes.length + footer.length];
        System.arraycopy(header, 0, body, 0, header.length);
        System.arraycopy(fileBytes, 0, body, header.length, fileBytes.length);
        System.arraycopy(footer, 0, body, header.length + fileBytes.length, footer.length);
        return body;
    }

    private void ensure2xx(HttpResponse<?> response, String action) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            String body = response.body() instanceof String ? ((String) response.body()) : "";
            throw new BizException(ErrorCode.ARK_API_ERROR,
                    action + " 返回非 2xx: status=" + status + ", body=" + body);
        }
    }

    private String normalizeUrl(String path) {
        String base = properties.getBaseUrl();
        if (base == null || base.isBlank()) {
            throw new BizException(ErrorCode.ARK_API_ERROR, "comfyui.base-url 未配置");
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            return base.substring(0, base.length() - 1) + path;
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }

    private String urlEncode(String value) {
        if (value == null) return "";
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
