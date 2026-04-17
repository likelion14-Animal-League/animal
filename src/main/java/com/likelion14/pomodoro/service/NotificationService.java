package com.likelion14.pomodoro.service;

import com.likelion14.pomodoro.entity.RoomGuest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
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
}