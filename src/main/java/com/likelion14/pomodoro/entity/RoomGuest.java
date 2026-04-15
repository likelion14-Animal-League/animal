package com.likelion14.pomodoro.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "room_guests")
@Getter
@NoArgsConstructor
public class RoomGuest {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id = UUID.randomUUID(); // PK (= guestId) [cite: 66]

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room; // FK -> rooms.id, CASCADE DELETE [cite: 66]

    @Column(length = 20, nullable = false)
    private String nickname; // 최대 20자 [cite: 14, 66]

    private String avatarId; // [cite: 66]

    @Column(length = 64, nullable = false, unique = true)
    private String guestToken; // crypto.randomBytes(32).hex() [cite: 66]

    private boolean isHost = false; // 방장 여부 [cite: 66]

    // isReady 필드는 요청하신 대로 제외되었습니다.

    private boolean isShielded = false; // 쉴드 활성 여부 [cite: 66]
    private LocalDateTime shieldUntil; /// 쉴드 만료 시각 [cite: 66]

    private Integer consecutiveHits = 0; // 연속 피격 횟수 (3이면 쉴드 발동) [cite: 66]
    private Integer pomodoroCount = 0; // 완료한 뽀모도로 수 [cite: 66]
    private Integer sentDisturbances = 0; // 발사한 방해 횟수 [cite: 66]
    private Integer receivedDisturbances = 0; // 받은 방해 횟수 [cite: 66]

    private LocalDateTime lastPingAt; // 출석 체크 최근 응답 시각 [cite: 66]
    private LocalDateTime joinedAt = LocalDateTime.now();

    public RoomGuest(Room room, String nickname, String avatarId, String guestToken, boolean isHost) {
        this.room = room;
        this.nickname = nickname;
        this.avatarId = avatarId;
        this.guestToken = guestToken;
        this.isHost = isHost;
    }
}