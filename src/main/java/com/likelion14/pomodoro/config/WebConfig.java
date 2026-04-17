package com.likelion14.pomodoro.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 만약 AuthInterceptor가 다른 패키지에 있다면 그것도 import 해야 합니다..
import com.likelion14.pomodoro.interceptor.AuthInterceptor;
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/v1/rooms/**") // .모든 방 관련 API에 적용
                .excludePathPatterns("/v1/rooms", "/v1/rooms/join"); // 생성과 참여는 토큰이 없으니 제외
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 모든 경로에 대해
                .allowedOrigins("http://localhost:5173") // Vite 프론트 주소 허용
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Guest-Token") // 중요: 커스텀 헤더를 프론트에서 읽으려면 노출 설정 필요
                .allowCredentials(true);
    }
}