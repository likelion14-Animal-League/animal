package com.likelion14.pomodoro.service;

import com.likelion14.pomodoro.entity.Room;
import com.likelion14.pomodoro.entity.RoomGuest;
import com.likelion14.pomodoro.repository.GuestRepository;
import com.likelion14.pomodoro.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

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
    public void completeCycle(String guestToken) {
        // 1. 현재 완료를 누른 사람(현재 팀장/방장) 찾기
        RoomGuest currentHost = guestRepository.findByGuestToken(guestToken)
                .orElseThrow(() -> new RuntimeException("GUEST_NOT_FOUND")); //
        Room room = currentHost.getRoom();

        // 2. 권한 확인: 팀장(is_host)만 사이클 완료 및 교체 가능 [cite: 11, 22]
        if (!currentHost.isHost()) {
            throw new RuntimeException("NOT_HOST"); //
        }

        // 3. 현재 팀장의 사이클 수 증가 및 상태 업데이트 [cite: 66]
        currentHost.completeCycle();
        notificationService.notifyGuestStatus(room.getId(), currentHost, "COMPLETED");

        // 4. 직전 팀장(본인)을 제외한 나머지 참여자 목록 확보
        List<RoomGuest> potentialCandidates = room.getGuests().stream()
                .filter(g -> !g.getId().equals(currentHost.getId()))
                .collect(Collectors.toList());

        if (!potentialCandidates.isEmpty()) {
            // --- [명세서 6번: 자동 방해 발사] ---
            // 나머지 인원 중 랜덤 한 명에게 방해 시전
            Collections.shuffle(potentialCandidates);
            RoomGuest targetToDisturb = potentialCandidates.get(0);
            this.disturbUser(guestToken, targetToDisturb.getId());

            // --- [명세서 5번: 팀장 교체 (Leader Rotate)] ---
            // 다시 섞어서 새로운 팀장 선출 (직전 팀장 제외 랜덤)
            Collections.shuffle(potentialCandidates);
            RoomGuest nextHost = potentialCandidates.get(0);

            // 권한 변경: 기존 팀장 해제 -> 새 팀장 승격
            currentHost.setHost(false);
            nextHost.setHost(true);

            // 5. 웹소켓 알림: leader:changed 이벤트 전송
            notificationService.notifyHostChanged(room.getId(), currentHost.getNickname(), nextHost.getNickname());

            // 추가로 명세서에 정의된 timer:completed 이벤트도 보낼 수 있습니다
            // notificationService.notifyTimerCompleted(room.getId(), currentHost.getId(), nextHost.getId());
        }
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
    public void disturbUser(String attackerToken, UUID targetGuestId) {
        // 1. 공격자 확인
        RoomGuest attacker = guestRepository.findByGuestToken(attackerToken)
                .orElseThrow(() -> new RuntimeException("공격자를 찾을 수 없습니다."));

        // 2. 타겟(수비자) 확인
        RoomGuest target = guestRepository.findById(targetGuestId)
                .orElseThrow(() -> new RuntimeException("대상자를 찾을 수 없습니다."));

        // 3. 방해 로직 (상대방이 쉴드 상태가 아닐 때만)
        if (target.isShielded()) {
            // 상대가 쉴드라면 공격 실패 알림만 보냄
            target.resetConsecutiveHits();
            notificationService.notifyDisturbResult(target.getRoom().getId(), attacker.getNickname(), target.getNickname(), "BLOCKED");
        } else {
            // 공격 성공: 상대방 타이머 일시정지
            target.incrementConsecutiveHits();
            target.pauseIndividualTimer();

            // [추가된 로직] 연속 3번 공격받으면 쉴드 활성화!
            String status = "SUCCESS";
            if (target.getConsecutiveHits() >= 3) {
                target.setShielded(true); // 쉴드 켜기
                target.resetConsecutiveHits(); // 콤보 초기화
                status = "SUCCESS_AND_SHIELD_ACTIVATED"; // 특수 상태값 전송
            }

            notificationService.notifyDisturbResult(target.getRoom().getId(), attacker.getNickname(), target.getNickname(), "SUCCESS");
        }
    }
}