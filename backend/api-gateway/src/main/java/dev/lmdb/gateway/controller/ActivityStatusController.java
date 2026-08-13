package dev.lmdb.gateway.controller;

import dev.lmdb.gateway.service.ActivityTrackingService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing gateway activity metrics for watchdog scripts and frontend telemetry.
 *
 * <p>Provides the {@code /actuator/activity} endpoint consumed by {@code auto-stop-watchdog.sh} to
 * decide whether to trigger cloud compute suspension after 1 hour of idle time, and by the frontend
 * footer to display real-time cloud provider, uptime, and time-to-sleep telemetry.
 *
 * @author LMDB Development Team
 * @version 1.1.0
 */
@Slf4j
@RestController
@RequestMapping("/actuator")
@RequiredArgsConstructor
public class ActivityStatusController {

  private final ActivityTrackingService activityTrackingService;

  @Value("${cloud.provider:${CLOUD_PROVIDER:azure}}")
  private String cloudProvider;

  /**
   * Resolves a human-readable display label for the active cloud provider.
   *
   * @param provider the raw provider identifier (e.g. azure, aws, minikube)
   * @return descriptive provider label
   */
  private String resolveProviderLabel(String provider) {
    if (provider == null) {
      return "Microsoft Azure (AKS)";
    }
    return switch (provider.toLowerCase()) {
      case "azure" -> "Microsoft Azure (AKS)";
      case "aws" -> "Amazon Web Services (k3s)";
      case "minikube" -> "Local Minikube Cluster";
      case "local", "docker" -> "Local Docker Infrastructure";
      default -> provider.substring(0, 1).toUpperCase() + provider.substring(1);
    };
  }

  /**
   * Returns current gateway activity, cloud provider telemetry, and auto-stop eligibility.
   *
   * @return JSON response with activity status, provider info, uptime, and threshold metrics
   */
  @GetMapping("/activity")
  public ResponseEntity<Map<String, Object>> getActivityStatus() {
    long threshold = ActivityTrackingService.DEFAULT_IDLE_THRESHOLD_SECONDS;
    long idleSeconds = activityTrackingService.getIdleSeconds();
    long remaining = activityTrackingService.getSecondsUntilAutoStop(threshold);
    String providerKey =
        (cloudProvider != null && !cloudProvider.isBlank()) ? cloudProvider.toLowerCase() : "azure";

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "UP");
    response.put("cloudProvider", providerKey);
    response.put("cloudProviderLabel", resolveProviderLabel(providerKey));
    response.put("serverStartTime", activityTrackingService.getStartTime().toString());
    response.put("uptimeSeconds", activityTrackingService.getUptimeSeconds());
    response.put("lastActivityTime", activityTrackingService.getLastActivityTime().toString());
    response.put("idleSeconds", idleSeconds);
    response.put("idleThresholdSeconds", threshold);
    response.put("secondsUntilAutoStop", remaining);
    response.put("autoStopEligible", idleSeconds >= threshold);

    return ResponseEntity.ok(response);
  }
}
