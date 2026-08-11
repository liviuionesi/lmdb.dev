package com.filmpire.gateway.controller;

import com.filmpire.gateway.service.ActivityTrackingService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing gateway activity metrics for watchdog scripts and frontend telemetry.
 *
 * <p>Provides the {@code /actuator/activity} endpoint consumed by {@code auto-stop-watchdog.sh} to
 * decide whether to trigger cloud compute suspension after 1 hour of idle time.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/actuator")
@RequiredArgsConstructor
public class ActivityStatusController {

  private final ActivityTrackingService activityTrackingService;

  /**
   * Returns current gateway activity and auto-stop eligibility.
   *
   * @return JSON response with activity status, idle duration, and threshold metrics
   */
  @GetMapping("/activity")
  public ResponseEntity<Map<String, Object>> getActivityStatus() {
    long threshold = ActivityTrackingService.DEFAULT_IDLE_THRESHOLD_SECONDS;
    long idleSeconds = activityTrackingService.getIdleSeconds();
    long remaining = activityTrackingService.getSecondsUntilAutoStop(threshold);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "UP");
    response.put("lastActivityTime", activityTrackingService.getLastActivityTime().toString());
    response.put("idleSeconds", idleSeconds);
    response.put("idleThresholdSeconds", threshold);
    response.put("secondsUntilAutoStop", remaining);
    response.put("autoStopEligible", idleSeconds >= threshold);

    return ResponseEntity.ok(response);
  }
}
