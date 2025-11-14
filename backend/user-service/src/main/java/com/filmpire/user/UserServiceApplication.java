package com.filmpire.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * User Service Application.
 * Manages user authentication, authorization, and profile data.
 * Eureka client is auto-configured when eureka-client dependency is present.
 */
@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

