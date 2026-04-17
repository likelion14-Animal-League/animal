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
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final GuestRepository guestRepository;
    private final NotificationService notificationService;

    @Transactional
    public Room createRoom(String nickname, String avatarId, Integer maxGuests, Integer pomoMin, Integer breakMin, String disturbLevel) {
        // 1. 방 코드 생성
        String roomCode;
        do {
            roomCode = generateRandomCode();
        } while (roomRepository.findByRoomCode(roomCode).isPresent());

        // 2. 방 저장
        Room room = new Room(roomCode, maxGuests, pomoMin, breakMin, disturbLevel);
        roomRepository.save(room);

        // 3. 방장 생성 및 저장
        String guestToken = generateHex64Token();
        RoomGuest host = new RoomGuest(room, nickname, avatarId, guestToken, true);

        // [중요] 컨트롤러에서 IndexOutOfBoundsException이 안 나게 리스트에 직접 추가
        room.getGuests().add(host);

        guestRepository.save(host);
        return room;
    }

    @Transactional
    public RoomGuest joinRoom(String roomCode, String nickname, String avatarId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("INVALID_ROOM_CODE"));

        if (guestRepository.countByRoom(room) >= room.getMaxGuests()) {
            throw new RuntimeException("ROOM_FULL");
        }

        String guestToken = generateHex64Token();
        RoomGuest guest = new RoomGuest(room, nickname, avatarId, guestToken, false);
        return guestRepository.save(guest);
    }

    @Transactional
    public String handleDisconnect(UUID roomId, String guestToken) {
        RoomGuest leaver = guestRepository.findByGuestToken(guestToken)
                .orElseThrow(() -> new RuntimeException("GUEST_NOT_FOUND"));

        UUID leaverId = leaver.getId();
        boolean wasHost = leaver.isHost();

        guestRepository.delete(leaver);
        guestRepository.flush();

        if (wasHost) {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("ROOM_NOT_FOUND"));

            return guestRepository.findByRoom(room).stream()
                    .filter(g -> !g.getId().equals(leaverId))
                    .findFirst()
                    .map(newHost -> {
                        newHost.promoteToHost();
                        guestRepository.save(newHost);
                        return newHost.getNickname();
                    }).orElse("공석");
        }
        return "일반 유저";
    }

    private String generateRandomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

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
    public void startAllTimers(UUID roomId, String hostToken) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));
        RoomGuest requester = guestRepository.findByGuestToken(hostToken)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));

        if (!requester.isHost()) {
            throw new RuntimeException("방장만 전체 시작을 할 수 있습니다.");
        }

        // 모든 참여자의 타이머 상태 On
        room.getGuests().forEach(RoomGuest::startIndividualTimer);
        room.startRoom();

        // 웹소켓 알림 전송
        notificationService.notifyTimerStart(roomId);
    }

    @Transactional
    public void pauseIndividual(String token) {
        RoomGuest guest = guestRepository.findByGuestToken(token).orElseThrow();
        guest.pauseIndividualTimer();
        notificationService.notifyGuestStatus(guest.getRoom().getId(), guest, "PAUSED");
    }

    @Transactional
    public void resumeIndividual(String token) {
        RoomGuest guest = guestRepository.findByGuestToken(token).orElseThrow();
        guest.startIndividualTimer();
        notificationService.notifyGuestStatus(guest.getRoom().getId(), guest, "RESUMED");
    }

    @Transactional
    public void completeCycle(String token) {
        RoomGuest guest = guestRepository.findByGuestToken(token).orElseThrow();
        guest.completeCycle();
        notificationService.notifyGuestStatus(guest.getRoom().getId(), guest, "COMPLETED");
    }
}