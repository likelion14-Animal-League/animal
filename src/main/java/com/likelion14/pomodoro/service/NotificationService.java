package com.likelion14.pomodoro.service;

import com.likelion14.pomodoro.entity.Disturbance;
import com.likelion14.pomodoro.entity.RoomGuest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final SimpMessagingTemplate messagingTemplate;

    public void notifyTimerStart(UUID roomId) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId, Map.of("type", "TIMER_START"));
    }

    public void notifyGuestStatus(UUID roomId, RoomGuest guest, String status) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId, Map.of(
                "type", "GUEST_STATUS_UPDATE",
                "nickname", guest.getNickname(),
                "status", status,
                "cycleCount", guest.getCycleCount()
        ));
    }

    // NotificationService.java 내부

    public void notifyDisturbance(UUID roomId, Disturbance disturbance) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "disturbance:received"); // 명세서 이벤트명
        payload.put("disturbanceId", disturbance.getId());
        payload.put("type", disturbance.getType()); // "minigame" 등 [cite: 34]
        payload.put("senderGuestId", disturbance.getAttacker().getId());

        // 명세서 규격에 맞춘 payload 상세 데이터 [cite: 34, 60]
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("gameType", disturbance.getType()); // "math", "mole" 등
        payload.put("payload", gameData);

        messagingTemplate.convertAndSend("/topic/room/" + roomId, payload);
    }

    // NotificationService.java 예시
    public void notifyDisturbResult(UUID roomId, String attacker, String target, String status) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "DISTURB_EVENT");
        message.put("attacker", attacker);
        message.put("target", target);
        message.put("status", status); // SUCCESS, BLOCKED, SUCCESS_AND_SHIELD_ACTIVATED

        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }
    // NotificationService.java
    public void notifyHostChanged(UUID roomId, String oldHost, String newHost) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId, Map.of(
                "type", "HOST_CHANGED",
                "oldHost", oldHost,
                "newHost", newHost,
                "message", "새로운 팀장은 " + newHost + "님입니다!"
        ));
    }
    // NotificationService.java 내부

    public void notifyDisturbanceCleared(Disturbance disturbance) {
        Map<String, Object> payload = new HashMap<>();

        payload.put("type", "disturbance:cleared"); // 명세서 이벤트명
        payload.put("disturbanceId", disturbance.getId()); //
        payload.put("targetGuestId", disturbance.getTarget().getId()); //

        // 클라이언트에 알림 전송
        messagingTemplate.convertAndSend("/topic/room/" + disturbance.getTarget().getRoom().getId(), payload);
    }
    // NotificationService.java

    // 1. 쉴드 활성화 알림 추가
    public void notifyShieldActivated(UUID roomId, UUID guestId) {
        // 웹소켓으로 해당 방의 모든 유저에게 쉴드 발동 소식을 알림
        // 이벤트명 예시: "shield:activated"
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "shield:activated");
        payload.put("guestId", guestId);

        // 혜리님이 기존에 쓰시던 메시지 전송 로직을 사용하세요.
        // 예: messagingTemplate.convertAndSend("/topic/room/" + roomId, payload);
        System.out.println("쉴드 활성화 알림 전송: " + guestId);
    }

    // 2. 쉴드 만료 알림도 미리 만들어두면 편합니다 (에러 방지)
    public void notifyShieldExpired(UUID roomId, UUID guestId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "shield:expired");
        payload.put("guestId", guestId);

        // messagingTemplate.convertAndSend("/topic/room/" + roomId, payload);
        System.out.println("쉴드 만료 알림 전송: " + guestId);
    }
    // NotificationService.java

    // 팀장(방장) 변경 알림 추가
    public void notifyLeaderChanged(UUID roomId, RoomGuest newLeader) {
        // 웹소켓으로 해당 방의 모든 유저에게 새로운 팀장 정보를 알림
        // 이벤트명 예시: "leader:changed"
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "leader:changed");
        payload.put("newLeaderId", newLeader.getId());
        payload.put("newLeaderNickname", newLeader.getNickname());

        // 혜리님이 기존에 메시지 전송할 때 쓰던 템플릿 로직을 적어주세요.
        // 예: messagingTemplate.convertAndSend("/topic/room/" + roomId, payload);

        System.out.println("팀장 변경 알림 전송: " + newLeader.getNickname());
    }

    // NotificationService.java

    // 출석 체크(Ping) 알림 추가
    public void notifyPing(UUID roomId) {
        // 웹소켓으로 해당 방의 모든 유저에게 "살아있니?"라고 물어보는 신호를 보냅니다.
        // 명세서 이벤트명: "attendance:ping"
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "attendance:ping");
        payload.put("sentAt", LocalDateTime.now());

        // 혜리님의 메시지 전송 로직 사용
        // messagingTemplate.convertAndSend("/topic/room/" + roomId, payload);

        System.out.println("방 [" + roomId + "] 출석 체크(Ping) 알림 전송");
    }
}