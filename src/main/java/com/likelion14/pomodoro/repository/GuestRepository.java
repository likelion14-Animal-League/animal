package com.likelion14.pomodoro.repository;

import com.likelion14.pomodoro.entity.Room;
import com.likelion14.pomodoro.entity.RoomGuest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List; // List import 잊지 마세요!

public interface GuestRepository extends JpaRepository<RoomGuest, java.util.UUID> {
    Optional<RoomGuest> findByGuestToken(String guestToken);

    // 이 메서드를 추가해야 합니다!s
    List<RoomGuest> findByRoom(Room room);

    long countByRoom(Room room);
}