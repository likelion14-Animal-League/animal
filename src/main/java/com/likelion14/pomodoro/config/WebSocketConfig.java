package com.likelion14.pomodoro.config;

import com.likelion14.pomodoro.handler.PomodoroWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final PomodoroWebSocketHandler pomodoroWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // ws://localhost:8080/ws/pomodoro .주소로 소켓 연결을 받음
        registry.addHandler(pomodoroWebSocketHandler, "/ws/pomodoro")
                .setAllowedOrigins("*"); // 테스트를 위해 모든 도메인 허용
    }
}