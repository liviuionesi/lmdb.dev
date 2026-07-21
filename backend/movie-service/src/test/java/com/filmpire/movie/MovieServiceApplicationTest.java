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
 * Smoke tests for MovieServiceApplication bootstrapping.
 * <p>
 * Boots the full Spring context against a real MongoDB 7.0 instance managed by Testcontainers;
 * caching, Eureka registration and Spring Cloud Config are disabled via
 * {@link DynamicPropertySource} so no infrastructure beyond a Docker daemon is required.
 * <p>
 * Maintainer note: the {@link ApplicationContext} is injected through the constructor, which
 * relies on the Jupiter/Spring extension activated by {@code @SpringBootTest}.
 */
@SpringBootTest
@Testcontainers
@DisplayName("MovieServiceApplication Tests")
class MovieServiceApplicationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    private final ApplicationContext applicationContext;

    /**
     * Receives the fully started context via constructor injection so it can be
     * inspected by the smoke tests below.
     */
    MovieServiceApplicationTest(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Points Spring Data at the Testcontainers-managed MongoDB and switches off caching, Eureka
     * and Cloud Config. Without these overrides the context would try to reach infrastructure
     * that does not exist in CI, and the smoke test would fail for the wrong reason.
     */
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.cache.type", () -> "none");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
    }

    /**
     * Stops the MongoDB container once the class is done. The running-state guard makes the
     * hook safe even when the container failed to start and every test was skipped.
     */
    @AfterAll
    static void cleanup() {
        if (mongoDBContainer != null && mongoDBContainer.isRunning()) {
            mongoDBContainer.stop();
        }
    }

    /**
     * Any broken bean definition (missing dependency, bad property, failed auto-configuration)
     * aborts context startup before this method runs, so merely reaching the assertion proves
     * the whole application wiring is valid against a real MongoDB.
     */
    @Test
    @DisplayName("Application context should load successfully")
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    /**
     * Guards against beans silently dropping out of component scanning (e.g. a package move or
     * class rename): the context can still start "successfully" without them, so the core
     * service, repository and both controllers are asserted by bean name.
     */
    @Test
    @DisplayName("Should have all required beans")
    void shouldHaveRequiredBeans() {
        assertThat(applicationContext.containsBean("movieService")).isTrue();
        assertThat(applicationContext.containsBean("movieRepository")).isTrue();
        assertThat(applicationContext.containsBean("movieController")).isTrue();
        assertThat(applicationContext.containsBean("genreController")).isTrue();
    }
}

