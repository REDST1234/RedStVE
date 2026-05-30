package com.bytedance.aivideo.creation.service;

import com.bytedance.aivideo.creation.dto.profile.CreationAsrResult;

import java.nio.file.Path;

/**
 * 创作流专属前置 ASR 提取服务
 */
public interface CreationAsrService {
    
    /**
     * 对音频文件进行轻量级结构化解析，提取人声和环境音。
     * @param audioFile 音频文件路径
     * @return 结构化的 ASR 结果
     */
    CreationAsrResult transcribe(Path audioFile);
}
