package com.bytedance.aivideo.deconstruct.service;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import com.bytedance.aivideo.config.ArkProperties;
import com.bytedance.aivideo.infrastructure.ark.ArkPayloadFactory;
import lombok.extern.slf4j.Slf4j;
import com.bytedance.aivideo.infrastructure.ark.ArkPromptTemplates;
import com.bytedance.aivideo.infrastructure.ark.ArkResponsesClient;
import com.bytedance.aivideo.infrastructure.ark.model.ArkInputMessage;
import com.bytedance.aivideo.infrastructure.ark.model.ArkResponseRequest;
import com.bytedance.aivideo.infrastructure.vector.BgmVectorProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.DigestUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class BgmKnowledgeService {

    private final ArkPayloadFactory arkPayloadFactory;
    private final ArkResponsesClient arkResponsesClient;
    private final ArkProperties arkProperties;
    private final BgmVectorProperties bgmVectorProperties;
    private final VectorStore bgmVectorStore;
    private final ObjectMapper objectMapper;

    public BgmKnowledgeService(ArkPayloadFactory arkPayloadFactory,
                               ArkResponsesClient arkResponsesClient,
                               ArkProperties arkProperties,
                               BgmVectorProperties bgmVectorProperties,
                               @Qualifier("creationBgmVectorStore") VectorStore bgmVectorStore,
                               ObjectMapper objectMapper) {
        this.arkPayloadFactory = arkPayloadFactory;
        this.arkResponsesClient = arkResponsesClient;
        this.arkProperties = arkProperties;
        this.bgmVectorProperties = bgmVectorProperties;
        this.bgmVectorStore = bgmVectorStore;
        this.objectMapper = objectMapper;
    }

    public Object processBgmUpload(MultipartFile file) {
        try {
            // 0. 计算文件 MD5 避免重复处理
            String md5 = DigestUtils.md5DigestAsHex(file.getBytes());

            // 1. 生成唯一 ID 与目录 (基于 MD5)
            String audioId = md5 + "_" + file.getOriginalFilename().replace(".mp3", "").replace(".wav", "");
            File baseDir = new File(bgmVectorProperties.getAudioDatabaseDir());
            if (!baseDir.exists()) {
                baseDir = new File("../" + bgmVectorProperties.getAudioDatabaseDir());
            }
            File dbDir = new File(baseDir, audioId);
            File jsonFile = new File(dbDir, "audio_data.json");

            // 如果该文件已经被分析过，直接返回本地缓存结果
            if (dbDir.exists() && jsonFile.exists()) {
                return objectMapper.readValue(jsonFile, Map.class);
            }

            if (!dbDir.exists()) {
                dbDir.mkdirs();
            }

            // 2. 保存文件
            Path targetPath = new File(dbDir, file.getOriginalFilename()).toPath();
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // 3. 构建多模态请求
            byte[] audioBytes = Files.readAllBytes(targetPath);
            String base64Audio = Base64.getEncoder().encodeToString(audioBytes);
            String format = file.getOriginalFilename().toLowerCase().endsWith(".mp3") ? "mp3" : "wav";

            List<ArkInputMessage> messages = arkPayloadFactory.buildAudioInput(base64Audio, format, ArkPromptTemplates.BGM_ANALYZER_JSON);

            ArkResponseRequest request = new ArkResponseRequest();
            request.setModel(arkProperties.getModel());
            request.setMessages(messages);

            // 4. 调用大模型
            JsonNode responseNode = arkResponsesClient.createResponse(request);
            String jsonResult = extractLlmContent(responseNode);

            // 5. 解析结果，注入基础信息
            Map<String, Object> data = objectMapper.readValue(jsonResult, new TypeReference<Map<String, Object>>() {});
            data.put("audioId", audioId);
            if (!data.containsKey("audioName") || data.get("audioName").toString().isBlank()) {
                data.put("audioName", file.getOriginalFilename());
            }
            
            // 构造正确的相对于项目的相对路径
            String relativePath = bgmVectorProperties.getAudioDatabaseDir() + "/" + audioId + "/" + file.getOriginalFilename();
            relativePath = relativePath.replace('\\', '/');
            data.put("filePath", relativePath);

            // 6. 保存为 audio_data.json
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, data);

            // 7. 刷入 ChromaDB VectorStore
            syncToVectorStore(data, audioId, data.get("audioName").toString());

            return data;
        } catch (Exception e) {
            e.printStackTrace();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "BGM 处理失败: " + e.getMessage());
        }
    }

    private String extractLlmContent(JsonNode responseNode) {
        if (responseNode == null || !responseNode.has("choices")) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "大模型未返回有效 choices");
        }
        JsonNode choices = responseNode.get("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "大模型 choices 为空");
        }
        JsonNode message = choices.get(0).get("message");
        if (message == null || !message.has("content")) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "大模型未返回 content");
        }
        String content = message.get("content").asText();
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        return content.trim();
    }

    private void syncToVectorStore(Map<String, Object> data, String audioId, String audioName) {
        try {
            Object gmObj = data.get("globalMetrics");
            Object lmObj = data.get("loudnessMetrics");

            Map<String, Object> globalMetrics = (gmObj instanceof Map) ? (Map<String, Object>) gmObj : null;
            Map<String, Object> loudnessMetrics = (lmObj instanceof Map) ? (Map<String, Object>) lmObj : null;

            String bpm = globalMetrics != null && globalMetrics.get("bpm") != null ? globalMetrics.get("bpm").toString() : "未知";
            String style = globalMetrics != null && globalMetrics.get("overallStyle") != null ? globalMetrics.get("overallStyle").toString() : "未知";
            String lufs = loudnessMetrics != null && loudnessMetrics.get("integratedLufs") != null ? loudnessMetrics.get("integratedLufs").toString() : "未知";
            double durationSeconds = 0.0;
            if (globalMetrics != null && globalMetrics.get("durationSeconds") instanceof Number durNum) {
                durationSeconds = durNum.doubleValue();
            }

            String textFeature = String.format("BGM: %s, 风格: %s, BPM: %s, 整体响度: %s LUFS. 这段音乐可用于匹配对应风格、节奏或响度需求的视频剪辑。",
                    audioName, style, bpm, lufs);

            Document document = new Document(audioId, textFeature, Map.of(
                    "audioId", audioId,
                    "audioName", audioName,
                    "folderName", audioId,
                    "overallStyle", style,
                    "style", style,
                    "bpm", bpm,
                    "durationSeconds", durationSeconds
            ));

            // 先删后加，防止重复分析时产生重复向量
            try {
                bgmVectorStore.delete(List.of(audioId));
            } catch (Exception ignored) {
                // collection may not exist yet, ignore
            }
            bgmVectorStore.add(List.of(document));
            log.info("BGM vector synced to Chroma: audioId={}, audioName={}, folderName={}, style={}, bpm={}, dur={}s",
                    audioId, audioName, audioId, style, bpm, durationSeconds);
        } catch (Exception e) {
            log.error("BGM vector sync to Chroma failed: audioId={}, audioName={}", audioId, audioName, e);
        }
    }

    public void deleteBgm(String audioId) {
        try {
            // 1. 删除向量库记录
            bgmVectorStore.delete(List.of(audioId));

            // 2. 删除本地文件夹
            File baseDir = new File(bgmVectorProperties.getAudioDatabaseDir());
            if (!baseDir.exists()) {
                baseDir = new File("../" + bgmVectorProperties.getAudioDatabaseDir());
            }
            File dbDir = new File(baseDir, audioId);
            if (dbDir.exists() && dbDir.isDirectory()) {
                File[] files = dbDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete();
                    }
                }
                dbDir.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "删除 BGM 失败: " + e.getMessage());
        }
    }
}
