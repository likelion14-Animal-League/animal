package com.likelion14.pomodoro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PomodoroApplication {

    public static void main(String[] args) {
        SpringApplication.run(PomodoroApplication.class, args);
    }

}
