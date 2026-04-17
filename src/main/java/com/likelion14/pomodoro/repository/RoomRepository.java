package com.likelion14.pomodoro.repository;

import com.likelion14.pomodoro.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    ///입장 코드로 방 찾기 [cite: 17, 68]s
    Optional<Room> findByRoomCode(String roomCode);
}