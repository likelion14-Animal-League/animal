package com.likelion14.pomodoro.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

public class JoinDto {

    @Getter @Setter
    public static class Request {
        private String roomCode;
        private String nickname;
        private String avatarId;
    }

    @Getter @Setter
    @AllArgsConstructor
    public static class Response {
        private UUID roomId;
        private UUID guestId;
        private String guestToken;
        private LocalDateTime expiresAt;
        private RoomInfo room;

        @Getter @Setter
        @AllArgsConstructor
        public static class RoomInfo {
            private Integer pomodoroMinutes;
            private String disturbLevel;
            private List<Participant> participants;
        }

        @Getter @Setter
        @AllArgsConstructor
        public static class Participant {
            private UUID guestId;
            private String nickname;
            private String avatarId;
            private boolean isHost;
        }
    }
}