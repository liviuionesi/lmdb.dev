package com.filmpire.movie;

import com.filmpire.movie.support.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for MovieServiceApplication bootstrapping.
 * <p>
 * Boots the full Spring context against the module's shared MongoDB
 * Testcontainer ({@link AbstractMongoIntegrationTest}); caching, Eureka
 * registration and Spring Cloud Config are disabled via
 * {@link DynamicPropertySource} so no infrastructure beyond a Docker daemon
 * is required.
 * <p>
 * Maintainer note: the {@link ApplicationContext} is injected through the constructor, which
 * relies on the Jupiter/Spring extension activated by {@code @SpringBootTest}.
 */
@SpringBootTest
@DisplayName("MovieServiceApplication Tests")
class MovieServiceApplicationTest extends AbstractMongoIntegrationTest {

    private final ApplicationContext applicationContext;

    /**
     * Receives the fully started context via constructor injection so it can be
     * inspected by the smoke tests below.
     */
    MovieServiceApplicationTest(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Switches off caching, Eureka and Cloud Config (the Mongo URI itself
     * comes from {@link AbstractMongoIntegrationTest}). Without these
     * overrides the context would try to reach infrastructure that does not
     * exist in CI, and the smoke test would fail for the wrong reason.
     */
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cache.type", () -> "none");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
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
