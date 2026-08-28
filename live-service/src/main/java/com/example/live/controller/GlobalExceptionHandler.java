package com.example.live.controller;

import com.example.live.common.ApiResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage()).orElse("请求参数不合法");
        return ApiResponse.error(400, message);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ApiResponse<Void> handleBadRequest(Exception exception) {
        String message = exception instanceof IllegalArgumentException
                ? exception.getMessage() : "请求格式不合法";
        int code = message != null && message.contains("不存在") ? 404 : 400;
        return ApiResponse.error(code, message == null ? "请求参数不合法" : message);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected() {
        return ApiResponse.error(500, "直播服务暂时不可用");
    }
}
