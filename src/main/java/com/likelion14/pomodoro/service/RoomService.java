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
        RoomGuest currentHost = guestRepository.findByGuestToken(guestToken)
                .orElseThrow(() -> new RuntimeException("GUEST_NOT_FOUND"));
        Room room = currentHost.getRoom();

        if (!currentHost.isHost()) {
            throw new RuntimeException("NOT_HOST");
        }

        currentHost.completeCycle();
        notificationService.notifyGuestStatus(room.getId(), currentHost, "COMPLETED");

        // 방장을 제외한 나머지 참여자들
        List<RoomGuest> potentialCandidates = room.getGuests().stream()
                .filter(g -> !g.getId().equals(currentHost.getId()))
                .collect(Collectors.toList());

        // 참여자가 1명 이상 있어야 교체 가능
        if (!potentialCandidates.isEmpty()) {
            Collections.shuffle(potentialCandidates);
            RoomGuest nextHost = potentialCandidates.get(0);

            // 권한 변경
            currentHost.setHost(false);
            nextHost.setHost(true);

            // DB에 명시적으로 저장 (변경 감지가 안 될 때를 대비)
            guestRepository.save(currentHost);
            guestRepository.save(nextHost);

            // 중요: 변경 사항을 즉시 DB에 꽂아넣음
            guestRepository.flush();

            notificationService.notifyHostChanged(room.getId(), currentHost.getNickname(), nextHost.getNickname());
        } else {
            // 혼자일 경우 로그를 찍어보면 디버깅이 편해요!
            System.out.println("대기 중인 참여자가 없어 방장이 유지됩니다.");
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
//        if (target.getConsecutiveHits() >= 3) {
//            target.setShielded(true);
//            target.resetConsecutiveHits();
//        } 테스트를 위해 주석처리
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

        // 2. 타겟에게 공통 방해 효과 적용
        applyDisturbanceEffects(target);

        // 3. 문제 및 정답 생성
        String problem;
        String solution;

        if ("baseball".equals(gameType)) {
            // 숫자 야구 타입인 경우
            problem = "숫자 야구: 3자리 숫자를 맞히세요!";
            solution = generateBaseballSolution();
        } else {
            // 기본은 사칙연산 (math) - 30% 확률로 혼합 연산
            Random random = new Random();
            Map<String, String> mathProblem = (random.nextInt(10) < 3)
                    ? generateMixedArithmetic()
                    : generateBasicArithmetic();
            problem = mathProblem.get("problem");
            solution = mathProblem.get("solution");
        }

        // 4. 방해 데이터 저장 (타입에 맞는 problem, solution이 들어감)
        Disturbance disturbance = new Disturbance(
                gameType,
                problem,
                solution,
                attacker,
                target
        );

        disturbanceRepository.save(disturbance);

        // 5. 알림 전송
        notificationService.notifyDisturbance(target.getRoom().getId(), disturbance);
    }

    @Transactional
    public String completeDisturbance(UUID disturbanceId, String userAnswer) {
        Disturbance disturbance = disturbanceRepository.findById(disturbanceId)
                .orElseThrow(() -> new RuntimeException("방해 데이터를 찾을 수 없습니다."));

        // 1. 숫자 야구인 경우
        if ("baseball".equals(disturbance.getType())) {
            String result = checkBaseball(disturbance.getSolution(), userAnswer);

            if ("3S 0B".equals(result)) { // 홈런인 경우
                handleDisturbanceSuccess(disturbance);
                return "홈런! 방해 해제 완료.";
            }
            return result; // "1S 2B" 같은 힌트 리턴 (isResolved는 false 유지)
        }

        // 2. 사칙연산인 경우
        else {
            if (disturbance.getSolution().equals(userAnswer)) {
                handleDisturbanceSuccess(disturbance);
                return "정답입니다!";
            } else {
                throw new RuntimeException("WRONG_ANSWER");
            }
        }
    }

    // 중복 코드를 줄이기 위한 헬퍼 메서드
    private void handleDisturbanceSuccess(Disturbance disturbance) {
        disturbance.complete();
        disturbanceRepository.saveAndFlush(disturbance);
        RoomGuest target = disturbance.getTarget();
        target.startIndividualTimer();
        notificationService.notifyDisturbanceCleared(disturbance);
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
    private String generateBaseballSolution() {
        List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9));
        Collections.shuffle(numbers);
        return numbers.get(0).toString() + numbers.get(1).toString() + numbers.get(2).toString();
    }

    public String checkBaseball(String solution, String answer) {
        int strikes = 0;
        int balls = 0;
        for (int i = 0; i < 3; i++) {
            if (answer.charAt(i) == solution.charAt(i)) strikes++;
            else if (solution.contains(String.valueOf(answer.charAt(i)))) balls++;
        }
        return strikes + "S " + balls + "B";
    }
}