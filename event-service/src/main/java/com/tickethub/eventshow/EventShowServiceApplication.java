package com.tickethub.eventshow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EventShowServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventShowServiceApplication.class, args);
    }
}
