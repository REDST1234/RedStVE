package com.bytedance.aivideo.common.api;

import com.bytedance.aivideo.common.error.ErrorCode;

/**
 * 统一 API 响应结构。
 *
 * @param code    业务码，成功固定为 "0"
 * @param message 响应消息
 * @param data    响应数据
 * @param <T>     数据类型
 */
public record ApiResponse<T>(String code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null);
    }
}
