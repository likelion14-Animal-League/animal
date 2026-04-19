package com.likelion14.pomodoro.repository;

import com.likelion14.pomodoro.entity.Disturbance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DisturbanceRepository extends JpaRepository<Disturbance, UUID> {
    List<Disturbance> findAllByTargetRoomIdOrderByCreatedAtDesc(UUID roomId);
}