package com.likelion14.pomodoro.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.Duration;
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

    // --- 명세서 v2.0 필수 필드  ---
    private boolean isShielded = false;
    private int consecutiveHits = 0; // 연속 피격 횟수
    private int pomodoroCount = 0;   // 완료한 뽀모도로 수

    @Column(name = "received_disturbances")
    private int receivedDisturbances = 0; // 받은 방해 횟수

    @Column(name = "sent_disturbances")
    private int sentDisturbances = 0;     // 발사한 방해 횟수

    // --- 타이머 관련 필드 ---
    private boolean isTimerRunning = false;
    private LocalDateTime lastStartedAt;
    private long accumulatedSeconds = 0;
    private int cycleCount = 0;

    public RoomGuest(Room room, String nickname, String avatarId, String guestToken, boolean isHost) {
        this.room = room;
        this.nickname = nickname;
        this.avatarId = avatarId;
        this.guestToken = guestToken;
        this.isHost = isHost;
    }

    // --- 비즈니스 로직 메서드 ---

    // 1. 권한 관련
    public void setHost(boolean host) {
        this.isHost = host;
    }

    public void promoteToHost() {
        this.isHost = true;
    }

    // 2. 타이머 관련
    public void startIndividualTimer() {
        this.isTimerRunning = true;
        this.lastStartedAt = LocalDateTime.now();
    }

    public void pauseIndividualTimer() {
        if (this.isTimerRunning && this.lastStartedAt != null) {
            long gap = Duration.between(this.lastStartedAt, LocalDateTime.now()).getSeconds();
            this.accumulatedSeconds += gap;
        }
        this.isTimerRunning = false;
    }

    public void completeCycle() {
        this.cycleCount++;
        this.pomodoroCount++; // 명세서 카운트도 같이 증가
        this.accumulatedSeconds = 0;
        this.isTimerRunning = false;
    }

    // 3. 방해 및 쉴드 관련 [cite: 41, 44]
    public void incrementConsecutiveHits() {
        this.consecutiveHits += 1;
    }

    public void resetConsecutiveHits() {
        this.consecutiveHits = 0;
    }

    public void setShielded(boolean isShielded) {
        this.isShielded = isShielded;
    }

    public void incrementReceivedDisturbances() {
        this.receivedDisturbances += 1;
    }

    public void incrementSentDisturbances() {
        this.sentDisturbances += 1;
    }
}