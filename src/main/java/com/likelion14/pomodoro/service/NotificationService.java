package com.likelion14.pomodoro.service;

import com.likelion14.pomodoro.entity.Disturbance;
import com.likelion14.pomodoro.entity.RoomGuest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

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
}