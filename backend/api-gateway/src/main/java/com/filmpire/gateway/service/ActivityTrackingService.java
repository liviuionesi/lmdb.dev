package com.filmpire.gateway.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for tracking user activity on the API Gateway.
 *
 * <p>Maintains the timestamp of the most recent user request to determine if the cluster has been
 * idle for the 1-hour threshold and can be safely scaled to zero / stopped.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class ActivityTrackingService {

  /** Default auto-stop inactivity threshold: 1 hour (3600 seconds). */
  public static final long DEFAULT_IDLE_THRESHOLD_SECONDS = 3600L;

  private final AtomicReference<Instant> lastActivityTime = new AtomicReference<>(Instant.now());

  /** Records that an active user request was processed by the gateway. */
  public void recordActivity() {
    Instant now = Instant.now();
    lastActivityTime.set(now);
    log.trace("Recorded user activity at {}", now);
  }

  /**
   * Returns the timestamp of the latest user request.
   *
   * @return Instant of last activity
   */
  public Instant getLastActivityTime() {
    return lastActivityTime.get();
  }

  /**
   * Calculates the number of seconds elapsed since the last user request.
   *
   * @return idle duration in seconds
   */
  public long getIdleSeconds() {
    return Duration.between(lastActivityTime.get(), Instant.now()).getSeconds();
  }

  /**
   * Calculates the remaining seconds before the 1-hour auto-stop threshold is reached.
   *
   * @param thresholdSeconds the configured inactivity threshold in seconds
   * @return remaining seconds until auto-stop, or 0 if threshold is already exceeded
   */
  public long getSecondsUntilAutoStop(long thresholdSeconds) {
    long idle = getIdleSeconds();
    return Math.max(0L, thresholdSeconds - idle);
  }

  /**
   * Resets the activity timer to the current instant (used during service startup or manual
   * keep-alive).
   */
  public void reset() {
    lastActivityTime.set(Instant.now());
  }
}
