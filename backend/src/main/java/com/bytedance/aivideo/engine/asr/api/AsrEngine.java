package com.bytedance.aivideo.engine.asr.api;

import com.bytedance.aivideo.engine.asr.model.AsrTranscriptionResult;

import java.nio.file.Path;

/**
 * ASR 引擎接口。
 */
public interface AsrEngine {

    /**
     * 转写指定音频文件。
     *
     * @param audioFile 音频文件路径
     * @param taskId    任务ID（用于日志追踪）
     * @return 结构化转写结果
     */
    AsrTranscriptionResult transcribe(Path audioFile, String taskId);
}

