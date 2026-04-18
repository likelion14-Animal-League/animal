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

    // 타이머 관련 필드
    private boolean isTimerRunning = false;
    private java.time.LocalDateTime lastStartedAt;
    private long accumulatedSeconds = 0;
    private int cycleCount = 0;

    // 타이머 로직 메서드들
    public void startIndividualTimer() {
        this.isTimerRunning = true;
        this.lastStartedAt = java.time.LocalDateTime.now();
    }

    public void pauseIndividualTimer() {
        if (this.isTimerRunning && this.lastStartedAt != null) {
            long gap = java.time.Duration.between(this.lastStartedAt, java.time.LocalDateTime.now()).getSeconds();
            this.accumulatedSeconds += gap;
        }
        this.isTimerRunning = false;
    }

    public void completeCycle() {
        this.cycleCount++;
        this.accumulatedSeconds = 0;
        this.isTimerRunning = false;
    }
    // RoomGuest.java
// 연속으로 방해받은 횟수 (콤보 등 활용 가능)
    public void setShield(boolean status) {
        this.isShielded = status;
    }
    // RoomGuest.java

    // 방해 성공 시 호출할 메서드
    public void incrementConsecutiveHits() {
        this.consecutiveHits += 1;
    }

    // 쉴드로 막거나 사이클 완료 시 초기화할 메서드
    public void resetConsecutiveHits() {
        this.consecutiveHits = 0;
    }
    // RoomGuest.java
    public void setShielded(boolean isShielded) {
        this.isShielded = isShielded;
    }
    // RoomGuest.java 내부

    // 팀장 권한을 주거나 뺏는 메서드
    public void setHost(boolean host) {
        this.isHost = host;
    }
}