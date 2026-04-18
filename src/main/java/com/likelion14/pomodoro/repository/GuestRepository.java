package com.likelion14.pomodoro.repository;

import com.likelion14.pomodoro.entity.Room;
import com.likelion14.pomodoro.entity.RoomGuest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface GuestRepository extends JpaRepository<RoomGuest, UUID> {
    Optional<RoomGuest> findByGuestToken(String guestToken);

    List<RoomGuest> findByRoom(Room room);

    long countByRoom(Room room);

    // [추가] 쉴드 만료 체크 스케줄러를 위해 필요합니다
    List<RoomGuest> findAllByIsShieldedTrue();

    // [추가] 방 ID로 게스트 목록을 조회할 때 사용합니다
    List<RoomGuest> findByRoomId(UUID roomId);
}