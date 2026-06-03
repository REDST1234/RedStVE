package com.bytedance.aivideo.infrastructure.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 启动时自动扫描并解析 storage/audio-database 中的音频及其元数据，同步至 Chroma creation_bgm 向量存储。
 */
@Slf4j
@Component
public class BgmVectorInitRunner implements CommandLineRunner {

    private final VectorStore vectorStore;
    private final BgmVectorProperties bgmVectorProperties;
    private final ObjectMapper objectMapper;

    public BgmVectorInitRunner(
            @Qualifier("creationBgmVectorStore") VectorStore vectorStore,
            BgmVectorProperties bgmVectorProperties,
            ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.bgmVectorProperties = bgmVectorProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("开始同步 BGM 音频资产至 Chroma creation_bgm VectorStore...");

        File dbDir = findDatabaseDir();
        if (dbDir == null || !dbDir.exists() || !dbDir.isDirectory()) {
            log.warn("未找到音频数据库目录 {}，跳过 BGM 同步。", bgmVectorProperties.getAudioDatabaseDir());
            return;
        }

        File[] subDirs = dbDir.listFiles(File::isDirectory);
        if (subDirs == null || subDirs.length == 0) {
            log.info("音频数据库中暂无音频文件夹，跳过同步。");
            return;
        }

        List<Document> documents = new ArrayList<>();

        for (File subDir : subDirs) {
            File jsonFile = new File(subDir, "audio_data.json");
            if (!jsonFile.exists() || !jsonFile.isFile()) {
                continue;
            }

            try {
                JsonNode rootNode = objectMapper.readTree(jsonFile);
                String audioId = rootNode.path("audioId").asText("");
                String audioName = rootNode.path("audioName").asText("");
                String filePath = rootNode.path("filePath").asText("");

                if (audioId.isBlank()) {
                    log.warn("音频文件 {} 的 audioId 为空，跳过。", jsonFile.getAbsolutePath());
                    continue;
                }

                JsonNode globalMetrics = rootNode.path("globalMetrics");
                int bpm = globalMetrics.path("bpm").asInt(120);
                String overallStyle = globalMetrics.path("overallStyle").asText("unknown");
                double durationSeconds = globalMetrics.path("durationSeconds").asDouble(0.0);

                // 拼接可描述的语义属性
                StringBuilder contentBuilder = new StringBuilder();
                contentBuilder.append("音频名: ").append(audioName).append("\n");
                contentBuilder.append("整体风格: ").append(overallStyle).append("\n");
                contentBuilder.append("BPM: ").append(bpm).append("\n");
                contentBuilder.append("时长: ").append(durationSeconds).append("秒\n");

                JsonNode timeline = rootNode.path("auditoryTimeline");
                if (timeline.isArray() && !timeline.isEmpty()) {
                    contentBuilder.append("听觉氛围及画面建议:\n");
                    for (JsonNode slice : timeline) {
                        double start = slice.path("timeRange").path("start").asDouble(0.0);
                        double end = slice.path("timeRange").path("end").asDouble(0.0);
                        String slot = slice.path("bestMatchedSlot").asText("unknown");
                        String mood = slice.path("auditoryPerception").path("moodVibe").asText("");
                        String pace = slice.path("auditoryPerception").path("perceivedPace").asText("");
                        String recommended = slice.path("recommendedMaterial").asText("");

                        contentBuilder.append(String.format("- [%.1fs - %.1fs] 角色: %s, 氛围: %s, 节奏节奏: %s, 推荐画面: %s\n",
                                start, end, slot, mood, pace, recommended));
                    }
                }

                Map<String, Object> metadata = Map.of(
                        "audioId", audioId,
                        "audioName", audioName,
                        "bpm", bpm,
                        "overallStyle", overallStyle,
                        "durationSeconds", durationSeconds,
                        "folderName", subDir.getName()
                );

                Document doc = new Document(audioId, contentBuilder.toString(), metadata);
                documents.add(doc);
                log.debug("解析 BGM 资产完成: ID={}, Name={}", audioId, audioName);
            } catch (IOException e) {
                log.error("解析音频描述文件失败: {}", jsonFile.getAbsolutePath(), e);
            }
        }

        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            log.info("成功将 {} 个 BGM 的语义特征刷入 Chroma creation_bgm VectorStore!", documents.size());
        } else {
            log.info("未发现任何合法的 BGM 数据同步至向量库。");
        }
    }

    private File findDatabaseDir() {
        String baseDir = bgmVectorProperties.getAudioDatabaseDir();
        // 尝试1：原样查找
        File f = new File(baseDir);
        if (f.exists() && f.isDirectory()) {
            return f;
        }
        // 尝试2：若相对 backend 运行，往上走一级
        f = new File("../" + baseDir);
        if (f.exists() && f.isDirectory()) {
            return f;
        }
        // 尝试3：尝试取用户工作空间绝对路径
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            Path path = Paths.get(userDir).resolve(baseDir);
            if (path.toFile().exists() && path.toFile().isDirectory()) {
                return path.toFile();
            }
            path = Paths.get(userDir).getParent().resolve(baseDir);
            if (path.toFile().exists() && path.toFile().isDirectory()) {
                return path.toFile();
            }
        }
        return null;
    }
}
