CREATE TABLE rooms (
                       id BINARY(16) PRIMARY KEY,
                       room_code VARCHAR(6) NOT NULL UNIQUE,
                       status ENUM('waiting', 'active', 'finished') DEFAULT 'waiting',
                       max_guests INT CHECK (max_guests BETWEEN 2 AND 4),
                       pomodoro_minutes INT DEFAULT 25,
                       break_minutes INT DEFAULT 5,
                       disturb_level VARCHAR(20),
                       expires_at TIMESTAMP NOT NULL,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE room_guests (
                             id BINARY(16) PRIMARY KEY,
                             room_id BINARY(16),
                             nickname VARCHAR(20) NOT NULL,
                             avatar_id VARCHAR(255),
                             guest_token VARCHAR(64) NOT NULL UNIQUE,
                             is_host BOOLEAN DEFAULT FALSE,
                             is_ready BOOLEAN DEFAULT FALSE,
                             is_shielded BOOLEAN DEFAULT FALSE,
                             shield_until TIMESTAMP NULL,
                             consecutive_hits INT DEFAULT 0,
                             pomodoro_count INT DEFAULT 0,
                             FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
);