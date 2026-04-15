package com.likelion14.pomodoro.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor
public class Room {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id = UUID.randomUUID();

    @Column(unique = true, length = 6, nullable = false)
    private String roomCode;

    @Enumerated(EnumType.STRING)
    private RoomStatus status = RoomStatus.waiting;

    private Integer maxGuests;
    private Integer pomodoroMinutes;
    private Integer breakMinutes;
    private String disturbLevel;

    // private LocalDateTime expiresAt; // <-- 이 줄을 삭제하거나 주석 처리하세요!
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoomGuest> guests = new ArrayList<>();

    public Room(String roomCode, Integer maxGuests, Integer pomodoroMinutes, Integer breakMinutes, String disturbLevel) {
        this.roomCode = roomCode;
        this.maxGuests = maxGuests;
        this.pomodoroMinutes = pomodoroMinutes;
        this.breakMinutes = breakMinutes;
        this.disturbLevel = disturbLevel;
        // this.expiresAt = LocalDateTime.now().plusHours(4); // <-- 이 줄도 삭제!
    }
}