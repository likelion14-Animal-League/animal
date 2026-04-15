package com.likelion14.pomodoro.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
import java.time.LocalDateTime;

public class RoomCreateDto {

    @Getter @Setter
    public static class CreateRequest {
        private String nickname;        // 최대 20자
        private String avatarId;
        private Integer maxGuests;      // 2 | 3 | 4
        private Integer pomodoroMinutes; // 기본 25
        private Integer breakMinutes;    // 기본 5
        private String disturbLevel;     // easy | normal | hard
    }

    @Getter @Setter
    public static class CreateResponse {
        private UUID roomId;
        private String roomCode;        // 6자리 대문자
        private UUID guestId;
        private String guestToken;      // hex64
        private LocalDateTime expiresAt; // ISO8601

        public CreateResponse(UUID roomId, String roomCode, UUID guestId, String guestToken, LocalDateTime expiresAt) {
            this.roomId = roomId;
            this.roomCode = roomCode;
            this.guestId = guestId;
            this.guestToken = guestToken;
            this.expiresAt = expiresAt;
        }
    }
}