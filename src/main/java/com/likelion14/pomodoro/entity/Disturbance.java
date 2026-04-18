package com.likelion14.pomodoro.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
public class Disturbance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String type; // "math", "word", "click" 등
    private String content; // 예: "12 + 25 = ?" (문제 내용)
    private String solution; // 예: "37" (정답)

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