package com.example.demo.config;

import com.example.demo.websocket.DanmuWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final DanmuWebSocketHandler danmuWebSocketHandler;

    public WebSocketConfig(DanmuWebSocketHandler danmuWebSocketHandler) {
        this.danmuWebSocketHandler = danmuWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(danmuWebSocketHandler, "/ws/danmu/{roomId}")
                .setAllowedOriginPatterns("*");
    }
}
