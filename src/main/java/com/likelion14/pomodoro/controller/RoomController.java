package com.likelion14.pomodoro.controller;

import com.likelion14.pomodoro.entity.Room;
import com.likelion14.pomodoro.entity.RoomGuest;
import com.likelion14.pomodoro.repository.RoomRepository;
import com.likelion14.pomodoro.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final RoomRepository roomRepository; // 조회를 위해 리포지토리 주입.

    // 1. 방 생성
    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody Map<String, Object> request) {
        Room room = roomService.createRoom(
                (String) request.get("nickname"),
                (String) request.get("avatarId"),
                (Integer) request.get("maxGuests"),
                (Integer) request.get("pomodoroMinutes"),
                (Integer) request.get("breakMinutes"),
                (String) request.get("disturb_level")
        );

        RoomGuest host = room.getGuests().stream()
                .filter(RoomGuest::isHost)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("방장 정보를 찾을 수 없습니다."));

        Map<String, Object> response = new HashMap<>();
        response.put("roomId", room.getId());
        response.put("roomCode", room.getRoomCode());
        response.put("guestToken", host.getGuestToken());
        response.put("role", "HOST");

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. 방 참여
    @PostMapping("/join")
    public ResponseEntity<?> joinRoom(@RequestBody Map<String, String> request) {
        RoomGuest guest = roomService.joinRoom(
                request.get("roomCode"),
                request.get("nickname"),
                request.get("avatarId")
        );

        Map<String, Object> response = new HashMap<>();
        response.put("roomId", guest.getRoom().getId());
        response.put("guestToken", guest.getGuestToken());
        response.put("role", "GUEST");

        return ResponseEntity.ok(response);
    }

    // 3. 방 상태 상세 조회 (이 부분이 없어서 404가 났던 것입니다!)
    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoom(@PathVariable UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));

        Map<String, Object> response = new HashMap<>();
        response.put("roomId", room.getId());
        response.put("roomCode", room.getRoomCode());
        response.put("status", room.getStatus());
        response.put("pomodoroMinutes", room.getPomodoroMinutes());
        response.put("breakMinutes", room.getBreakMinutes());

        // 참여자 목록 변환
        List<Map<String, Object>> participants = room.getGuests().stream().map(g -> {
            Map<String, Object> p = new HashMap<>();
            p.put("guestId", g.getId());
            p.put("nickname", g.getNickname());
            p.put("avatarId", g.getAvatarId());
            p.put("isHost", g.isHost());
            p.put("isShielded", g.isShielded());
            p.put("isTimerRunning", g.isTimerRunning());
            p.put("cycleCount", g.getCycleCount());
            return p;
        }).collect(Collectors.toList());

        response.put("participants", participants);

        return ResponseEntity.ok(response);
    }
    // 4. 타이머 전체 시작 (방장 전용)
    @PostMapping("/{roomId}/start")
    public ResponseEntity<?> startTimer(
            @PathVariable UUID roomId,
            @RequestHeader("X-Guest-Token") String guestToken) {

        roomService.startAllTimers(roomId, guestToken); // 서비스 메서드명 확인!
        return ResponseEntity.ok(Map.of("message", "타이머가 시작되었습니다."));
    }

    // 5. 내 타이머 일시정지
    @PostMapping("/me/pause")
    public ResponseEntity<?> pauseMe(@RequestHeader("X-Guest-Token") String token) {
        roomService.pauseIndividual(token);
        return ResponseEntity.ok(Map.of("message", "일시정지 완료"));
    }

    // 6. 내 타이머 재개
    @PostMapping("/me/resume")
    public ResponseEntity<?> resumeMe(@RequestHeader("X-Guest-Token") String token) {
        roomService.resumeIndividual(token);
        return ResponseEntity.ok(Map.of("message", "재개 완료"));
    }

    // 7. 한 바퀴 완료
    @PostMapping("/me/complete")
    public ResponseEntity<?> completeMe(@RequestHeader("X-Guest-Token") String token) {
        roomService.completeCycle(token);
        return ResponseEntity.ok(Map.of("message", "바퀴 수 증가 완료"));
    }
}
