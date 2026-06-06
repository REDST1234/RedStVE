package com.bytedance.aivideo.creation.dto;

import lombok.Data;

/**
 * 直接提交 JSON 编排脚本进行渲染的响应。
 */
@Data
public class RenderFromJsonResponse {
    /** 渲染任务唯一标识，可用于轮询状态或获取输出文件 */
    private String renderId;
    /** 任务状态：QUEUED / FAILED */
    private String status;
    /** 失败时的错误信息 */
    private String error;
    /** 渲染完成后可通过此 URL 获取视频: out/{renderId}.mp4 */
    private String outputUrlTemplate;
}
