package com.likelion14.pomodoro.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Getter
@NoArgsConstructor
public class Disturbance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String type; // "math", "word", "click" 등
    // Disturbance.java
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String solution;

    @ManyToOne
    private RoomGuest attacker;

    @ManyToOne
    private RoomGuest target;

    private boolean isResolved = false; // 해결 여부
    private LocalDateTime createdAt = LocalDateTime.now();

    public Disturbance(String type, String content, String solution, RoomGuest attacker, RoomGuest target) {
        this.type = type;
        this.content = content;
        this.solution = solution;
        this.attacker = attacker;
        this.target = target;
        this.isResolved = false;
        this.createdAt = LocalDateTime.now();
    }

    public void complete() {
        this.isResolved = true;
    }


}