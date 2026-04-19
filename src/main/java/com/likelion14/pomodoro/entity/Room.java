package com.likelion14.pomodoro.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
public class Room {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id = UUID.randomUUID();
    private UUID currentLeaderId;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(unique = true, length = 6, nullable = false)
    private String roomCode;

    @Enumerated(EnumType.STRING)
    private RoomStatus status = RoomStatus.waiting;

    private Integer maxGuests;
    private Integer pomodoroMinutes;
    private Integer breakMinutes;
    private String disturbLevel;

    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoomGuest> guests = new ArrayList<>();

    public Room(String roomCode, Integer maxGuests, Integer pomodoroMinutes, Integer breakMinutes, String disturbLevel) {
        this.roomCode = roomCode;
        this.maxGuests = maxGuests;
        this.pomodoroMinutes = pomodoroMinutes;
        this.breakMinutes = breakMinutes;
        this.disturbLevel = disturbLevel;
        this.expiresAt = LocalDateTime.now().plusHours(4);

    }
    // Room.java 에 이 메서드를 추가하세요
    public void startRoom() {
        // 현재 엔티티의 status 필드를 active 상태로 변경합니다.
        this.status = RoomStatus.active;
    }
}