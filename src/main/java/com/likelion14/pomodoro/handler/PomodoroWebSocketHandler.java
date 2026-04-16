package com.likelion14.pomodoro.handler;

import com.likelion14.pomodoro.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class PomodoroWebSocketHandler extends TextWebSocketHandler {

    private final RoomService roomService;
    // <RoomId, <SessionId, WebSocketSession>> 구조로 방별 세션 관리
    private final Map<String, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 1. 연결 시 URI 쿼리 파라미터에서 roomId 추출 (ws://.../ws/pomodoro?roomId=XXXX)
        String query = session.getUri().getQuery();
        String roomId = "default"; // 기본값

        if (query != null && query.contains("roomId=")) {
            // "roomId=" 이후의 값을 가져옴
            roomId = query.split("roomId=")[1].split("&")[0];
        }

        // 2. 해당 방 바구니에 내 세션 추가
        roomSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .put(session.getId(), session);

        // 3. 나중에 꺼내 쓰기 편하게 세션 내부 속성에도 roomId 저장
        session.getAttributes().put("roomId", roomId);

        System.out.println("웹소켓 연결 성공! [방 ID: " + roomId + "] [세션 ID: " + session.getId() + "]");
    }

    /**
     * 특정 방(roomId)에 있는 모든 사용자에게 메시지 전송
     * @param roomId 대상 방 ID
     * @param type 메시지 타입 (예: HOST_CHANGED, START_TIMER 등)
     * @param message 전달할 실제 내용
     */
    public void broadcastToRoom(String roomId, String type, String message) throws Exception {
        // 클라이언트가 받기 편하게 JSON 형식으로 조립
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

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomId = (String) session.getAttributes().get("roomId");

        if (roomId != null) {
            // 1. 세션 제거
            roomSessions.get(roomId).remove(session.getId());

            // 2. [핵심] 방장 위임 로직 실행
            // 서비스에서 새 방장 이름을 받아와서 해당 방 사람들에게만 쏩니다.
            try {
                String newHostName = roomService.delegateHost(UUID.fromString(roomId));
                broadcastToRoom(roomId, "HOST_CHANGED", "방장이 나갔습니다. 새로운 방장은 " + newHostName + "님입니다.");
            } catch (Exception e) {
                System.out.println("방에 남은 인원이 없거나 위임 실패: " + e.getMessage());
            }
        }
        System.out.println("웹소켓 종료 및 위임 체크 완료");
    }
}