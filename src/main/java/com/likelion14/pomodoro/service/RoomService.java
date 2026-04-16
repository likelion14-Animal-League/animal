package com.likelion14.pomodoro.service;

import com.likelion14.pomodoro.entity.Room;
import com.likelion14.pomodoro.entity.RoomGuest;
import com.likelion14.pomodoro.repository.GuestRepository;
import com.likelion14.pomodoro.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final GuestRepository guestRepository;

// 1. 방 생성 로직/
    @Transactional
    public Room createRoom(String nickname, String avatarId, Integer maxGuests, Integer pomoMin, Integer breakMin, String disturbLevel) {
        // 랜덤 6자리 방 코드 생성 (중복 체크 포함)
        String roomCode;
        do {
            roomCode = generateRandomCode();
        } while (roomRepository.findByRoomCode(roomCode).isPresent());

        // 방 엔티티 생성 및 저장
        Room room = new Room(roomCode, maxGuests, pomoMin, breakMin, disturbLevel);
        roomRepository.save(room);

        // 방장(Guest) 등록 및 토큰 발급
        String guestToken = generateHex64Token();
        RoomGuest host = new RoomGuest(room, nickname, avatarId, guestToken, true);

        // [핵심 수정 포인트] 메모리 상의 room 객체에도 host를 넣어줘야 컨트롤러가 바로 읽을 수 있습니다.
        if (room.getGuests() != null) {
            room.getGuests().add(host);
        }

        guestRepository.save(host);

        return room;
    }

    // 2. 방 참여 로직
    @Transactional
    public RoomGuest joinRoom(String roomCode, String nickname, String avatarId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("INVALID_ROOM_CODE")); // 400 에러 대응

        // 정원 초과 체크
        if (guestRepository.countByRoom(room) >= room.getMaxGuests()) {
            throw new RuntimeException("ROOM_FULL"); // 409 에러 대응
        }

        // 새로운 게스트 등록
        String guestToken = generateHex64Token();
        RoomGuest guest = new RoomGuest(room, nickname, avatarId, guestToken, false);
        return guestRepository.save(guest);
    }

    // 랜덤 코드 생성기 (대문자 + 숫자 6자리)
    private String generateRandomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // 64자 헥사 토큰 생성 (crypto.randomBytes(32).hex() 대응)
    private String generateHex64Token() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    @Transactional
    public String delegateHost(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));

        // 1. 현재 방장이 있는지 확인 (이미 나갔으면 없을 수 있음)
        // 2. 남은 멤버 중 한 명을 선택 (여기서는 리스트의 첫 번째 사람)
        return room.getGuests().stream()
                .filter(g -> !g.isHost()) // 현재 방장이 아닌 사람 중
                .findFirst()
                .map(newHost -> {
                    // 새로운 방장으로 승격시키는 로직 (엔티티에 메서드가 필요할 수 있음)
                    // newHost.promoteToHost(); // 예시
                    return newHost.getNickname();
                }).orElse("공석");
    }
}