package com.filmpire.movie;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MovieServiceApplication.
 * Verifies that the application context loads correctly.
 */
@SpringBootTest
@Testcontainers
@DisplayName("MovieServiceApplication Tests")
class MovieServiceApplicationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    private final ApplicationContext applicationContext;

    MovieServiceApplicationTest(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.cache.type", () -> "none");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
    }

    @AfterAll
    static void cleanup() {
        if (mongoDBContainer != null && mongoDBContainer.isRunning()) {
            mongoDBContainer.stop();
        }
    }

    @Test
    @DisplayName("Application context should load successfully")
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    @DisplayName("Should have all required beans")
    void shouldHaveRequiredBeans() {
        assertThat(applicationContext.containsBean("movieService")).isTrue();
        assertThat(applicationContext.containsBean("movieRepository")).isTrue();
        assertThat(applicationContext.containsBean("movieController")).isTrue();
        assertThat(applicationContext.containsBean("genreController")).isTrue();
    }
}

