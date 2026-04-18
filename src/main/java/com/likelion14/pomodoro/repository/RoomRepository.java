package com.likelion14.pomodoro.repository;

import com.likelion14.pomodoro.entity.Room;
import com.likelion14.pomodoro.entity.RoomStatus; // Status Enum 확인 필수!
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByRoomCode(String roomCode);

    // [추가] 대기 중이거나 진행 중인 방만 골라서 Ping을 보내기 위해 필요합니다
    List<Room> findAllByStatus(RoomStatus status);
}