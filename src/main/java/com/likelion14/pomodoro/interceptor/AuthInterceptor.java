package com.likelion14.pomodoro.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("X-Guest-Token");

        // 1. 토큰이 없으면 입구컷
        if (token == null || token.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 에러
            return false;
        }

        // 2. 여기서 서비스나 레포지토리를 사용해 실제 DB에 있는 토큰인지 검사하는 로직을 넣어야 합니다.

        return true;
    }
}