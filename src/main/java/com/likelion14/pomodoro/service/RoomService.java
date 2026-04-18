package com.likelion14.pomodoro.service;

import com.likelion14.pomodoro.entity.Disturbance;
import com.likelion14.pomodoro.entity.Room;
import com.likelion14.pomodoro.entity.RoomGuest;
import com.likelion14.pomodoro.repository.DisturbanceRepository;
import com.likelion14.pomodoro.repository.GuestRepository;
import com.likelion14.pomodoro.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final GuestRepository guestRepository;
    private final NotificationService notificationService;
    private final DisturbanceRepository disturbanceRepository;

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
    private void applyDisturbanceEffects(RoomGuest target) {
        target.incrementReceivedDisturbances();
        target.incrementConsecutiveHits();
        target.pauseIndividualTimer();

        // 연속 3번 맞으면 쉴드 활성화 및 콤보 초기화
        if (target.getConsecutiveHits() >= 3) {
            target.setShielded(true);
            target.resetConsecutiveHits();
        }
    }

    @Transactional
    public void disturbUser(String attackerToken, UUID targetGuestId) {
        RoomGuest attacker = guestRepository.findByGuestToken(attackerToken)
                .orElseThrow(() -> new RuntimeException("공격자를 찾을 수 없습니다."));
        RoomGuest target = guestRepository.findById(targetGuestId)
                .orElseThrow(() -> new RuntimeException("대상자를 찾을 수 없습니다."));

        if (target.isShielded()) {
            target.resetConsecutiveHits();
            notificationService.notifyDisturbResult(target.getRoom().getId(), attacker.getNickname(), target.getNickname(), "BLOCKED");
        } else {
            // 공통 효과 적용
            applyDisturbanceEffects(target);
            notificationService.notifyDisturbResult(target.getRoom().getId(), attacker.getNickname(), target.getNickname(), "SUCCESS");
        }
    }

    @Transactional
    public void launchMinigame(String attackerToken, UUID targetGuestId, String gameType) {
        RoomGuest attacker = guestRepository.findByGuestToken(attackerToken)
                .orElseThrow(() -> new RuntimeException("GUEST_NOT_FOUND"));
        RoomGuest target = guestRepository.findById(targetGuestId)
                .orElseThrow(() -> new RuntimeException("GUEST_NOT_FOUND"));

        // 쉴드 체크
        if (target.isShielded()) {
            throw new RuntimeException("SHIELD_ACTIVE");
        }

        // 1. 공격자 수치 업데이트
        attacker.incrementSentDisturbances();

        // 2. 타겟에게 공통 방해 효과 적용 (받은 횟수+, 콤보+, 타이머 정지, 쉴드체크)
        applyDisturbanceEffects(target);

        // 3. 문제 생성 (30% 확률로 혼합 연산)
        Random random = new Random();
        Map<String, String> mathProblem = (random.nextInt(10) < 3)
                ? generateMixedArithmetic()
                : generateBasicArithmetic();

        // 4. 방해 데이터 저장
        Disturbance disturbance = new Disturbance(
                gameType,
                mathProblem.get("problem"),
                mathProblem.get("solution"),
                attacker,
                target
        );
        disturbanceRepository.save(disturbance);

        // 5. 알림 전송
        notificationService.notifyDisturbance(target.getRoom().getId(), disturbance);
    }

    @Transactional
    public void completeDisturbance(UUID disturbanceId, String userAnswer) {
        Disturbance disturbance = disturbanceRepository.findById(disturbanceId)
                .orElseThrow(() -> new RuntimeException("방해 데이터를 찾을 수 없습니다."));

        // 정답 확인
        if (disturbance.getSolution().equals(userAnswer)) {
            disturbance.complete(); // 해결 상태로 변경

            // 1. 타겟 유저의 타이머 다시 시작!
            RoomGuest target = disturbance.getTarget();
            target.startIndividualTimer();

            // 2. 웹소켓으로 "방해 끝남" 알림
            notificationService.notifyDisturbanceCleared(disturbance);
        } else {
            throw new RuntimeException("WRONG_ANSWER"); // 틀리면 다시 풀게 함
        }
    }
    private Map<String, String> generateMathProblem() {
        Random random = new Random();
        int num1 = random.nextInt(90) + 10; // 10~99
        int num2 = random.nextInt(90) + 10;

        String problem = num1 + " + " + num2 + " = ?";
        String solution = String.valueOf(num1 + num2);

        return Map.of("problem", problem, "solution", solution);
    }
    private Map<String, String> generateMixedArithmetic() {
        Random random = new Random();
        int a = random.nextInt(10) + 2;
        int b = random.nextInt(10) + 2;
        int c = random.nextInt(20) + 5;

        // 유형 1: (a * b) + c
        // 유형 2: (a + b) * c
        // 유형 3: c - (a * b)
        int type = random.nextInt(3);
        String problem;
        int solution;

        switch (type) {
            case 0:
                problem = String.format("(%d * %d) + %d = ?", a, b, c);
                solution = (a * b) + c;
                break;
            case 1:
                problem = String.format("(%d + %d) * %d = ?", a, b, c);
                solution = (a + b) * c;
                break;
            default:
                problem = String.format("%d - (%d * %d) = ?", c + (a * b), a, b);
                solution = c; // 음수 방지를 위해 결과값을 c로 고정하도록 역산
                break;
        }

        return Map.of("problem", problem, "solution", String.valueOf(solution));
    }

    private Map<String, String> generateBasicArithmetic() {
        Random random = new Random();
        String[] ops = {"+", "-", "*", "/"};
        String op = ops[random.nextInt(4)];
        int n1, n2, res;

        if (op.equals("*")) {
            n1 = random.nextInt(9) + 2;
            n2 = random.nextInt(9) + 2;
            res = n1 * n2;
        } else if (op.equals("/")) {
            n2 = random.nextInt(8) + 2;
            res = random.nextInt(9) + 2;
            n1 = n2 * res; // 딱 떨어지게 설정
        } else if (op.equals("-")) {
            n1 = random.nextInt(50) + 20;
            n2 = random.nextInt(19) + 1;
            res = n1 - n2;
        } else {
            n1 = random.nextInt(50) + 1;
            n2 = random.nextInt(50) + 1;
            res = n1 + n2;
        }

        return Map.of("problem", n1 + " " + op + " " + n2 + " = ?", "solution", String.valueOf(res));
    }
}