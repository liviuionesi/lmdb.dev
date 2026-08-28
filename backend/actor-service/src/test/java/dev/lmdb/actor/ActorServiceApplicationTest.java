package dev.lmdb.actor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Smoke tests for {@link ActorServiceApplication} bootstrapping and core bean registration.
 *
 * <p>Boots the complete Spring context against a real PostgreSQL Testcontainer to guarantee that
 * Flyway migrations, JPA entity mappings, and Spring components wire cleanly without configuration
 * drift.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("ActorServiceApplication Tests")
class ActorServiceApplicationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  /**
   * Disables Redis caching, Eureka discovery, and Spring Cloud Config for context smoke testing so
   * no external services beyond PostgreSQL are required.
   *
   * @param registry dynamic property registry
   */
  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.cache.type", () -> "none");
    registry.add("eureka.client.enabled", () -> "false");
    registry.add("spring.cloud.config.enabled", () -> "false");
  }

  @Autowired private ApplicationContext applicationContext;

  /**
   * Verifies that the Spring {@link ApplicationContext} initializes successfully without bean
   * circularities, unresolvable properties, or migration failures.
   */
  @Test
  @DisplayName("Application context should load successfully")
  void contextLoads() {
    // Then
    assertThat(applicationContext).isNotNull();
  }

  /**
   * Verifies that all foundational beans across service, repository, and controller layers are
   * discovered and managed by the Spring IoC container.
   */
  @Test
  @DisplayName("Should have all required beans registered")
  void shouldHaveRequiredBeans() {
    // Then
    assertThat(applicationContext.containsBean("actorService")).isTrue();
    assertThat(applicationContext.containsBean("actorRepository")).isTrue();
    assertThat(applicationContext.containsBean("actorController")).isTrue();
    assertThat(applicationContext.containsBean("personFacadeController")).isTrue();
    assertThat(applicationContext.containsBean("clock")).isTrue();
  }

  /**
   * Verifies that the injected {@link Clock} bean is configured to the UTC time zone to ensure
   * uniform timestamp generation across environments.
   */
  @Test
  @DisplayName("Clock bean should be UTC")
  void clockBeanIsUtc() {
    // When
    Clock clock = applicationContext.getBean(Clock.class);

    // Then
    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
  }
}
