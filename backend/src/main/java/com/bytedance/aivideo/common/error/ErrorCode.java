package com.bytedance.aivideo.common.error;

/**
 * 业务错误码定义。
 */
public enum ErrorCode {
    SUCCESS("0", "success"),
    VIDEO_FORMAT_UNSUPPORTED("VIDEO_FORMAT_UNSUPPORTED", "不支持的视频格式"),
    VIDEO_TOO_LARGE("VIDEO_TOO_LARGE", "视频文件超过大小限制"),
    FFMPEG_ERROR("FFMPEG_ERROR", "FFprobe 探测失败"),
    AUDIO_EXTRACT_ERROR("AUDIO_EXTRACT_ERROR", "音轨提取失败"),
    ARK_API_ERROR("ARK_API_ERROR", "方舟接口调用失败"),
    ASR_ERROR("ASR_ERROR", "ASR 调用失败"),
    ASR_RESPONSE_INVALID("ASR_RESPONSE_INVALID", "ASR 响应格式无效"),
    TASK_NOT_FOUND("TASK_NOT_FOUND", "任务不存在"),
    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND", "项目不存在"),
    MATERIAL_NOT_FOUND("MATERIAL_NOT_FOUND", "素材不存在"),
    MATERIAL_DELETE_FAILED("MATERIAL_DELETE_FAILED", "素材删除失败"),
    INVALID_REQUEST("INVALID_REQUEST", "请求参数不合法"),
    INTERNAL_ERROR("INTERNAL_ERROR", "系统内部错误");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
