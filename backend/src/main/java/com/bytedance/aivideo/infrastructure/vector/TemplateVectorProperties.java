package com.bytedance.aivideo.infrastructure.vector;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "creation.template-vector")
public class TemplateVectorProperties {
    /**
     * 独立用于存放模板资产向量的 Chroma 集合名称
     */
    private String collectionName = "creation_template";
}
