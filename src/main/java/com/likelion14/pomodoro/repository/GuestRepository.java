package com.likelion14.pomodoro.repository;

import com.likelion14.pomodoro.entity.RoomGuest;
import com.likelion14.pomodoro.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface GuestRepository extends JpaRepository<RoomGuest, UUID> {
    // guestToken으로 게스트 존재 여부 확인 (인증용) [cite: 4, 6, 59]
    Optional<RoomGuest> findByGuestToken(String guestToken);

    // 방에 속한 게스트 인원수 체크 [cite: 16, 68]
    long countByRoom(Room room);
}