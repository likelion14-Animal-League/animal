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
@CrossOrigin(origins = "http://localhost:5173")
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

    // [추가] 시나리오 4: 방 나가기 및 자동 삭제 테스트용
    @PostMapping("/{roomId}/leave")
    public ResponseEntity<?> leaveRoom(
            @PathVariable UUID roomId,
            @RequestHeader("X-Guest-Token") String token) {

        // 서비스의 handleDisconnect 호출
        String result = roomService.handleDisconnect(roomId, token);

        Map<String, Object> response = new HashMap<>();
        response.put("result", result);

        // 방이 삭제되었는지, 아니면 그냥 유저만 나갔는지 메시지 전달
        if ("ROOM_DELETED".equals(result)) {
            response.put("message", "마지막 참여자가 퇴장하여 방이 삭제되었습니다.");
        } else {
            response.put("message", "퇴장 처리가 완료되었습니다. 다음 방장: " + result);
        }

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
            // RoomController.java의 getRoom 메서드 내부 participants 맵핑 부분
            p.put("consecutiveHits", g.getConsecutiveHits()); // 이 줄을 추가하세요!

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

    // RoomController.java

    @GetMapping("/sessions/{roomId}/disturbance/history")
    public ResponseEntity<?> getDisturbHistory(@PathVariable UUID roomId) {
        Map<String, Object> history = roomService.getDisturbanceHistory(roomId);
        return ResponseEntity.ok(history);
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

    // RoomController.java
    // [수정] 7. 한 바퀴 완료 (명세서 v2.0: POST /v1/sessions/{sessionId}/complete)
    // 컨트롤러 상단이 /v1/rooms 라면 주소를 아래처럼 명시적으로 잡아야 합니다.
    @PostMapping("/sessions/{roomId}/complete")
    public ResponseEntity<?> completeMe(
            @PathVariable UUID roomId, // 명세서 규격상 sessionId를 받음
            @RequestHeader("X-Guest-Token") String token) {
        roomService.completeCycle(token);
        return ResponseEntity.ok(Map.of("message", "바퀴 수 증가 및 팀장 교체 완료"));
    }

    // [수정] 방해하기 (명세서 v2.0: POST /{sessionId}/disturbance)
    @PostMapping("/sessions/{roomId}/disturbance")
    public ResponseEntity<?> disturb(
            @PathVariable UUID roomId,
            @RequestHeader("X-Guest-Token") String attackerToken,
            @RequestBody Map<String, String> request) {

        // 명세서에는 targetId를 바디(JSON)로 받거나 쿼리로 받도록 되어 있을 겁니다.
        // 여기서는 명세서 6번 규격에 맞춰 targetId를 가져옵니다.
        UUID targetId = UUID.fromString(request.get("targetGuestId"));
        String gameType = request.getOrDefault("gameType", "math");

        roomService.launchMinigame(attackerToken, targetId, gameType);
        return ResponseEntity.ok(Map.of("message", "미니게임 발사 완료"));
    }

    // SessionController 또는 RoomController
    @PostMapping("/sessions/disturbances/{disturbanceId}/complete")
    public ResponseEntity<?> completeDisturbance(
            @PathVariable UUID disturbanceId,
            @RequestBody Map<String, String> request) {

        String answer = request.get("answer");
        roomService.completeDisturbance(disturbanceId, answer);

        return ResponseEntity.ok(Map.of("message", "검증 요청 완료"));
    }

    @PostMapping("/sessions/{roomId}/leader/rotate")
    public ResponseEntity<?> rotateLeader(
            @PathVariable UUID roomId,
            @RequestHeader("X-Guest-Token") String token) {

        // 1. (선택사항) 토큰을 가진 유저가 방장인지 권한 체크 로직이 필요할 수 있습니다.

        // 2. 서비스의 rotateLeader 호출
        RoomGuest newLeader = roomService.rotateLeader(roomId);

        if (newLeader == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "교체할 참여자가 없습니다."));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("newLeaderId", newLeader.getId());
        response.put("newLeaderNickname", newLeader.getNickname());
        response.put("message", "팀장이 성공적으로 교체되었습니다.");

        return ResponseEntity.ok(response);
    }
}
