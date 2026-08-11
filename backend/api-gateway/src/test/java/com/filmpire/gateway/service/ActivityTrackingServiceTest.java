package com.filmpire.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ActivityTrackingService}.
 *
 * <p>Validates timestamp tracking, idle duration math, and auto-stop threshold calculations.
 */
@DisplayName("ActivityTrackingService Tests")
class ActivityTrackingServiceTest {

  private ActivityTrackingService service;

  /** Initializes a fresh service before each test. */
  @BeforeEach
  void setUp() {
    service = new ActivityTrackingService();
  }

  /** Service starts with an activity timestamp initialized to approximately the current instant. */
  @Test
  @DisplayName("Should initialize with current timestamp")
  void shouldInitializeWithCurrentTimestamp() {
    Instant initial = service.getLastActivityTime();
    assertThat(initial).isNotNull();
    assertThat(service.getIdleSeconds()).isLessThanOrEqualTo(2L);
  }

  /** Recording activity updates the last activity timestamp. */
  @Test
  @DisplayName("Should update timestamp when activity is recorded")
  void shouldUpdateTimestampOnActivity() throws InterruptedException {
    // Given
    Instant initial = service.getLastActivityTime();
    Thread.sleep(10);

    // When
    service.recordActivity();

    // Then
    Instant updated = service.getLastActivityTime();
    assertThat(updated).isAfterOrEqualTo(initial);
  }

  /** Idle time threshold calculation returns accurate remaining seconds. */
  @Test
  @DisplayName("Should compute remaining seconds until auto-stop")
  void shouldComputeRemainingSecondsUntilAutoStop() {
    // Given 3600 second threshold
    long threshold = 3600L;

    // When
    long remaining = service.getSecondsUntilAutoStop(threshold);

    // Then
    assertThat(remaining).isGreaterThan(3590L).isLessThanOrEqualTo(3600L);
  }

  /** Reset restores last activity timestamp to now. */
  @Test
  @DisplayName("Should reset activity timer")
  void shouldResetActivityTimer() {
    service.reset();
    assertThat(service.getIdleSeconds()).isLessThanOrEqualTo(1L);
  }
}
