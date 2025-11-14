package com.filmpire.actor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Actor Service Application.
 * Manages actor/cast information, biographies, and filmographies.
 * Eureka client is auto-configured when eureka-client dependency is present.
 */
@SpringBootApplication
public class ActorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActorServiceApplication.class, args);
    }
}

