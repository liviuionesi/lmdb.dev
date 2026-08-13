package com.filmpire.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.filmpire.gateway.service.ActivityTrackingService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for {@link ActivityStatusController}.
 *
 * <p>Verifies that {@code /actuator/activity} returns expected metrics formatted for external
 * watchdogs.
 */
@DisplayName("ActivityStatusController Tests")
class ActivityStatusControllerTest {

  private ActivityTrackingService activityTrackingService;
  private ActivityStatusController controller;

  /** Sets up mocks before each test. */
  @BeforeEach
  void setUp() {
    activityTrackingService = mock(ActivityTrackingService.class);
    controller = new ActivityStatusController(activityTrackingService);
  }

  /** Controller returns HTTP 200 with status, idle seconds, and auto-stop flags. */
  @Test
  @DisplayName("Should return 200 OK with activity metrics")
  void getActivityStatus_shouldReturnMetrics() {
    // Given
    Instant fixedTime = Instant.parse("2026-08-11T09:00:00Z");
    Instant startTime = Instant.parse("2026-08-11T08:00:00Z");
    when(activityTrackingService.getLastActivityTime()).thenReturn(fixedTime);
    when(activityTrackingService.getStartTime()).thenReturn(startTime);
    when(activityTrackingService.getUptimeSeconds()).thenReturn(3600L);
    when(activityTrackingService.getIdleSeconds()).thenReturn(120L);
    when(activityTrackingService.getSecondsUntilAutoStop(3600L)).thenReturn(3480L);

    // When
    ResponseEntity<Map<String, Object>> response = controller.getActivityStatus();

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.get("status")).isEqualTo("UP");
    assertThat(body.get("cloudProvider")).isEqualTo("azure");
    assertThat(body.get("cloudProviderLabel")).isEqualTo("Microsoft Azure (AKS)");
    assertThat(body.get("serverStartTime")).isEqualTo(startTime.toString());
    assertThat(body.get("uptimeSeconds")).isEqualTo(3600L);
    assertThat(body.get("lastActivityTime")).isEqualTo(fixedTime.toString());
    assertThat(body.get("idleSeconds")).isEqualTo(120L);
    assertThat(body.get("secondsUntilAutoStop")).isEqualTo(3480L);
    assertThat(body.get("autoStopEligible")).isEqualTo(false);
  }
}
