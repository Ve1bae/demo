package com.example.video.controller;

import com.example.video.common.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleBadRequest(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "请求参数不合法" : exception.getMessage();
        int code = message.contains("不存在") ? 404 : 400;
        return ApiResponse.error(code, message);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception exception) {
        log.error("Unhandled video-service request failure", exception);
        return ApiResponse.error(500, "视频服务暂时不可用");
    }
}
