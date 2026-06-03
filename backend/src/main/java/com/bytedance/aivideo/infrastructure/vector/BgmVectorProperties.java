package com.bytedance.aivideo.infrastructure.vector;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "creation.bgm-vector")
public class BgmVectorProperties {
    /**
     * 独立用于存放 BGM 音频资产向量的 Chroma 集合名称
     */
    private String collectionName = "creation_bgm";

    /**
     * 音频素材数据库路径，默认为相对路径 /storage/audio-database
     */
    private String audioDatabaseDir = "storage/audio-database";
}
