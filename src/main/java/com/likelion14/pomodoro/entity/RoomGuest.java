package com.likelion14.pomodoro.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "room_guests")
@Getter
@NoArgsConstructor
public class RoomGuest {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    private String nickname;
    private String avatarId;

    @Column(unique = true)
    private String guestToken;

    private boolean isHost = false;

    // 명세서 v2.0 필수 필드들s
    private boolean isShielded = false;
    private int consecutiveHits = 0;
    private int pomodoroCount = 0;

    public void promoteToHost() {
        this.isHost = true;
    }

    public RoomGuest(Room room, String nickname, String avatarId, String guestToken, boolean isHost) {
        this.room = room;
        this.nickname = nickname;
        this.avatarId = avatarId;
        this.guestToken = guestToken;
        this.isHost = isHost;
        this.isShielded = false;
        this.consecutiveHits = 0;
        this.pomodoroCount = 0;
    }
}