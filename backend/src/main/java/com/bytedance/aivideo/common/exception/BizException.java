package com.bytedance.aivideo.common.exception;

import com.bytedance.aivideo.common.error.ErrorCode;

/**
 * 业务异常，统一携带错误码与可读消息。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
