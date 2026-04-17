package com.likelion14.pomodoro.handler;

import com.likelion14.pomodoro.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class PomodoroWebSocketHandler extends TextWebSocketHandler {

    private final RoomService roomService;
    private final Map<String, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri().getQuery();
        String roomId = extractParam(query, "roomId");
        String token = extractParam(query, "token");

        // 나중에 끊길 때 사용하기 위해 세션에 저장s
        session.getAttributes().put("roomId", roomId);
        session.getAttributes().put("token", token);

        if (roomId != null) {
            roomSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                    .put(session.getId(), session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomId = (String) session.getAttributes().get("roomId");
        String token = (String) session.getAttributes().get("token");

        if (roomId != null && token != null) {
            // 메모리 세션 제거
            if (roomSessions.containsKey(roomId)) {
                roomSessions.get(roomId).remove(session.getId());
            }

            try {
                // DB 유저 삭제 및 방장 위임 실행
                String result = roomService.handleDisconnect(UUID.fromString(roomId), token);

                if (!result.equals("일반 유저") && !result.equals("공석")) {
                    broadcastToRoom(roomId, "HOST_CHANGED", "새 방장: " + result);
                }
                broadcastToRoom(roomId, "GUEST_LEFT", "누군가 퇴장했습니다.");
            } catch (Exception e) {
                System.err.println("퇴장 처리 중 오류 발생: " + e.getMessage());
            }
        }
    }

    private String extractParam(String query, String name) {
        if (query == null) return null;
        return Arrays.stream(query.split("&"))
                .filter(s -> s.startsWith(name + "="))
                .map(s -> s.split("=")[1])
                .findFirst().orElse(null);
    }

    public void broadcastToRoom(String roomId, String type, String message) throws Exception {
        String jsonMessage = String.format("{\"type\": \"%s\", \"message\": \"%s\"}", type, message);
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);

        if (sessions != null) {
            for (WebSocketSession session : sessions.values()) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonMessage));
                }
            }
        }
    }
}