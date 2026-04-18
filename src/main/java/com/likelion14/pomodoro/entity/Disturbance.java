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

    private String generateBaseballSolution() {
        List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9));
        Collections.shuffle(numbers);
        // 중복 없는 3자리 숫자 생성
        return numbers.stream()
                .limit(3)
                .map(String::valueOf)
                .collect(Collectors.joining());
    }

}