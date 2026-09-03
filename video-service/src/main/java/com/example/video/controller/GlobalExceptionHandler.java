package com.example.video.controller;

import com.example.video.common.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "请求参数不合法" : exception.getMessage();
        int code = message.contains("不存在") ? 404 : 400;
        // Keep the existing video API convention: missing resources use HTTP 200 with business code 404.
        if (code == 404) return ResponseEntity.ok(ApiResponse.error(code, message));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(code, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled video-service request failure", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "视频服务暂时不可用"));
    }
}
