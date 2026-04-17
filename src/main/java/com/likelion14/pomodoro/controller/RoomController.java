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
            return p;
        }).collect(Collectors.toList());

        response.put("participants", participants);

        return ResponseEntity.ok(response);
    }
}
//-방 생성 (POST /v1/rooms)
//방 설정(시간, 난이도 등) 저장 및 6자리 랜덤 코드 생성.
//생성자를 방장(Host)으로 설정하고 guestToken 발급.
//
//        -방 참여 (POST /v1/rooms/join)
//방 코드로 입장 및 일반 유저(Guest) 토큰 발급.
//
//        -방 상태 조회 (GET /v1/rooms/{roomId})
//방 정보 및 현재 참여 중인 유저 목록(참여자 정보) 반환.
//
//        -실시간 유저 관리 (WebSocket)
//웹소켓 연결 시 유저 세션 관리.
//퇴장 처리: 웹소켓 연결 종료 시 DB에서 해당 유저 삭제 및 방장 위임(Host Migration) 로직 구현 완료.
//
//        -엔티티 설계 고도화
//isReady 제거(명세서 기반 간소화), isShielded, consecutiveHits 등 방해하기 기능용 필드 구성.