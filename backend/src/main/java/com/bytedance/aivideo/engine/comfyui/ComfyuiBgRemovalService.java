package com.bytedance.aivideo.engine.comfyui;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.ComfyuiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * ComfyUI 抠图业务编排服务。
 * <p>
 * 负责：上传图片 → 注入 workflow → 提交执行 → 轮询完成 → 下载抠图结果 → 覆盖原文件。
 */
@Service
@Slf4j
public class ComfyuiBgRemovalService {

    private final ComfyuiClient comfyuiClient;
    private final ComfyuiProperties properties;
    private final ObjectMapper objectMapper;

    public ComfyuiBgRemovalService(ComfyuiClient comfyuiClient,
                                   ComfyuiProperties properties,
                                   ObjectMapper objectMapper) {
        this.comfyuiClient = comfyuiClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 对输入图片执行背景移除，输出覆盖原文件。
     *
     * @param inputImage 待抠图的本地图片路径
     * @return 抠图后的文件路径（同 inputImage）
     */
    public Path removeBackground(Path inputImage) {
        if (!Files.exists(inputImage)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "抠图输入图片不存在: " + inputImage);
        }

        // 1. 上传原图
        Map<String, String> uploadResult = comfyuiClient.uploadImage(inputImage);
        String uploadedName = uploadResult.get("name");

        // 2. 加载并注入 workflow
        String workflowJson = loadAndInjectWorkflow(uploadedName);

        // 3. 提交执行
        String promptId = comfyuiClient.submitWorkflow(workflowJson);

        // 4. 轮询完成
        JsonNode outputs = comfyuiClient.pollHistory(promptId);

        // 5. 从 outputs 提取第一个 SaveImage 节点的输出文件名
        String outFilename = null;
        String outSubfolder = "";
        String outType = "output";
        var fieldNames = outputs.fieldNames();
        while (fieldNames.hasNext()) {
            String nodeId = fieldNames.next();
            JsonNode nodeOutput = outputs.path(nodeId);
            if (nodeOutput.has("images") && nodeOutput.path("images").isArray()
                    && !nodeOutput.path("images").isEmpty()) {
                JsonNode firstImage = nodeOutput.path("images").get(0);
                outFilename = firstImage.path("filename").asText();
                outSubfolder = firstImage.path("subfolder").asText("");
                outType = firstImage.path("type").asText("output");
                break;
            }
        }

        if (outFilename == null || outFilename.isBlank()) {
            throw new BizException(ErrorCode.ARK_API_ERROR,
                    "ComfyUI 完成但未找到输出图片: promptId=" + promptId);
        }

        // 6. 下载结果覆盖原文件
        Path tempOutput = inputImage.resolveSibling("_bg_removed_" + inputImage.getFileName().toString());
        try {
            comfyuiClient.downloadOutput(outFilename, outSubfolder, outType, tempOutput);
            // 用抠图结果替换原文件
            Files.move(tempOutput, inputImage, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (BizException ex) {
            try { Files.deleteIfExists(tempOutput); } catch (Exception ignored) { }
            throw ex;
        } catch (Exception ex) {
            try { Files.deleteIfExists(tempOutput); } catch (Exception ignored) { }
            throw new BizException(ErrorCode.INTERNAL_ERROR, "抠图结果处理失败: " + ex.getMessage());
        }

        log.info("comfyui bg removal done: input={}, output={}, promptId={}", inputImage, inputImage, promptId);
        return inputImage;
    }

    /**
     * 加载工作流 JSON 文件并将 filename 注入到 LoadImage 节点。
     */
    private String loadAndInjectWorkflow(String filename) {
        Path workflowPath = resolveWorkflowPath();
        log.info("loading comfyui workflow from: {}", workflowPath.toAbsolutePath());

        String raw;
        try {
            raw = Files.readString(workflowPath);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_REQUEST,
                    "无法读取 ComfyUI 工作流文件: " + workflowPath.toAbsolutePath() + " — " + ex.getMessage());
        }

        try {
            JsonNode root = objectMapper.readTree(raw);
            boolean injected = false;

            var fieldNames = root.fieldNames();
            while (fieldNames.hasNext()) {
                String nodeId = fieldNames.next();
                JsonNode node = root.path(nodeId);
                if ("LoadImage".equals(node.path("class_type").asText(""))) {
                    if (node.has("inputs") && node.path("inputs").isObject()) {
                        ((ObjectNode) node.path("inputs")).put("image", filename);
                        injected = true;
                        log.info("injected filename '{}' into LoadImage node '{}'", filename, nodeId);
                    }
                }
            }

            if (!injected) {
                log.warn("workflow JSON 中未找到 LoadImage 节点，请确认工作流包含图片加载节点");
            }
            return objectMapper.writeValueAsString(root);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_REQUEST,
                    "ComfyUI 工作流 JSON 解析失败: " + ex.getMessage());
        }
    }

    private Path resolveWorkflowPath() {
        String configured = properties.getWorkflowPath();
        if (configured == null || configured.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "comfyui.workflow-path 未配置");
        }
        Path path = Paths.get(configured);
        if (path.isAbsolute()) {
            if (Files.exists(path)) return path;
            throw new BizException(ErrorCode.INVALID_REQUEST,
                    "ComfyUI 工作流文件不存在: " + path.toAbsolutePath());
        }
        // 相对路径：依次尝试 CWD、CWD/backend（兼容从 monorepo 根目录启动）
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate = cwd.resolve(configured);
        if (Files.exists(candidate)) return candidate;
        Path backendCandidate = cwd.resolve("backend").resolve(configured);
        if (Files.exists(backendCandidate)) return backendCandidate;
        throw new BizException(ErrorCode.INVALID_REQUEST,
                "ComfyUI 工作流文件未找到，已尝试: " + candidate.toAbsolutePath() + ", " + backendCandidate.toAbsolutePath());
    }
}
