package com.filmpire.movie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MovieServiceApplication.
 * Verifies that the application context loads correctly.
 */
@SpringBootTest
@DisplayName("MovieServiceApplication Tests")
class MovieServiceApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> "mongodb://localhost:27017/test");
        registry.add("spring.cache.type", () -> "none");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
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

