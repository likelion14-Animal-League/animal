package com.likelion14.pomodoro.interceptor;

import com.likelion14.pomodoro.repository.GuestRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final GuestRepository guestRepository; // 레포지토리 주입

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("X-Guest-Token");

        // 1. 토큰이 비어있거나, DB에 존재하지 않는 토큰이면 401 에러
        if (token == null || guestRepository.findByGuestToken(token).isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        return true; // 인증 성공!
    }
}