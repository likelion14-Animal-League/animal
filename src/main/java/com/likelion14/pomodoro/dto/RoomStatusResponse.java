package com.likelion14.pomodoro.dto;

import com.likelion14.pomodoro.entity.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.UUID;

@Getter @Setter
@AllArgsConstructor // [roomId, roomCode, status, participants, currentLeaderId] 순서로 생성자 생성
public class RoomStatusResponse {
    private UUID roomId;
    private String roomCode;
    private RoomStatus status;
    private List<ParticipantDetail> participants;
    private UUID currentLeaderId;

    @Getter @Setter
    @AllArgsConstructor
    public static class ParticipantDetail {
        private UUID guestId;
        private String nickname;
        private String avatarId;
        private boolean isHost;
        private boolean isShielded;
        private Integer pomodoroCount;
    }
}