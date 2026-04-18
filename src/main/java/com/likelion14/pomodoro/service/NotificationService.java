package com.likelion14.pomodoro.service;

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
}