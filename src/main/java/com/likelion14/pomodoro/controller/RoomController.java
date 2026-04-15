package com.likelion14.pomodoro.controller;

import com.likelion14.pomodoro.dto.RoomStatusResponse;
import com.likelion14.pomodoro.entity.Room;
import com.likelion14.pomodoro.entity.RoomGuest;
import com.likelion14.pomodoro.repository.RoomRepository;
import com.likelion14.pomodoro.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final RoomRepository roomRepository;

    // 방 생성 API
    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody Map<String, Object> body) {
        Room room = roomService.createRoom(
                (String) body.get("nickname"),
                (String) body.get("avatarId"),
                (Integer) body.get("maxGuests"),
                (Integer) body.get("pomodoroMinutes"),
                (Integer) body.get("breakMinutes"),
                (String) body.get("disturbLevel")
        );

        // 방장 정보 추출 (첫 번째 게스트)
        RoomGuest host = room.getGuests().get(0);

        return ResponseEntity.ok(Map.of(
                "roomId", room.getId(),
                "roomCode", room.getRoomCode(),
                "guestId", host.getId(),
                "guestToken", host.getGuestToken(),
                "expiresAt", room.getExpiresAt()
        ));
    }

    // 방 상세 정보 조회 API
    @GetMapping("/{roomId}")
    public ResponseEntity<RoomStatusResponse> getRoomStatus(@PathVariable UUID roomId) {
        // 1. DB에서 방 찾기 (없으면 에러)
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));

        // 2. 참여자 목록을 DTO 형태(ParticipantDetail)로 변환
        // boolean 타입의 getter는 롬복 규칙에 따라 isHost(), isShielded()를 사용합니다.
        List<RoomStatusResponse.ParticipantDetail> participants = room.getGuests().stream()
                .map(g -> new RoomStatusResponse.ParticipantDetail(
                        g.getId(),
                        g.getNickname(),
                        g.getAvatarId(),
                        g.isHost(),
                        g.isShielded(),
                        g.getPomodoroCount()
                )).toList();

        // 3. 방장(Host)의 ID 찾기
        UUID hostId = participants.stream()
                .filter(RoomStatusResponse.ParticipantDetail::isHost)
                .findFirst()
                .map(RoomStatusResponse.ParticipantDetail::getGuestId)
                .orElse(null);

        // 4. 최종 응답 반환
        return ResponseEntity.ok(new RoomStatusResponse(
                room.getId(),
                room.getRoomCode(),
                room.getStatus(),
                room.getExpiresAt(),
                participants,
                hostId
        ));
    }

    // 방 참여 API
    @PostMapping("/join")
    public ResponseEntity<?> joinRoom(@RequestBody Map<String, String> body) {
        RoomGuest guest = roomService.joinRoom(
                body.get("roomCode"),
                body.get("nickname"),
                body.get("avatarId")
        );

        Room room = guest.getRoom();

        return ResponseEntity.ok(Map.of(
                "roomId", room.getId(),
                "guestId", guest.getId(),
                "guestToken", guest.getGuestToken(),
                "expiresAt", room.getExpiresAt()
        ));
    }
}