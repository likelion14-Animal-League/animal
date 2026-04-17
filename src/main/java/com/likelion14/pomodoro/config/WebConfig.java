package com.likelion14.pomodoro.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
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
}