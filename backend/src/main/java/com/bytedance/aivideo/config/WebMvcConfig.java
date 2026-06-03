package com.bytedance.aivideo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /api/storage/** 请求映射到本地的 storage/ 文件夹，方便前端播放音频/视频
        registry.addResourceHandler("/api/storage/**")
                .addResourceLocations("file:storage/");
    }
}
