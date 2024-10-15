package com.rexec.server;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final HelloController helloController;

    public WebSocketConfig(HelloController helloController) {
        this.helloController = helloController;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(helloController, "/ws") // 使用HelloController处理WebSocket请求
                .setAllowedOrigins("*"); // 允许所有来源
    }
}
