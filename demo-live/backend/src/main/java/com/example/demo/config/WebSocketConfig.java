package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.example.demo.websocket.DanmuWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final DanmuWebSocketHandler danmuWebSocketHandler;

    public WebSocketConfig(DanmuWebSocketHandler danmuWebSocketHandler) {
        this.danmuWebSocketHandler = danmuWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 允许跨域，方便前端调试
        registry.addHandler(danmuWebSocketHandler, "/ws/danmu/{roomId}")
                .setAllowedOrigins("*");
    }
}