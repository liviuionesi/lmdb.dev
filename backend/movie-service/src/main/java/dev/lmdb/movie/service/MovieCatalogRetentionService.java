package dev.lmdb.movie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled service enforcing catalog retention and growth control for MongoDB.
 *
 * <p>List endpoints upsert basic movie stubs into MongoDB to seed the catalog, but stubs that are
 * never queried for details would otherwise grow the database without limit. This service
 * periodically purges list-sourced stubs older than {@code movie.catalog.retention-days} (default:
 * 7 days).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MovieCatalogRetentionService {

  private final MovieService movieService;

  @Value("${movie.catalog.retention-days:7}")
  private int retentionDays;

  /** Runs daily at 03:00 UTC to purge stale list-sourced stubs. */
  @Scheduled(cron = "${movie.catalog.retention-cron:0 0 3 * * ?}")
  public void runScheduledRetentionCleanup() {
    log.info(
        "Starting scheduled catalog retention cleanup (retention threshold: {} days)...",
        retentionDays);
    long deletedCount = movieService.cleanupListSourcedStubs(retentionDays);
    log.info("Scheduled catalog retention cleanup complete. Evicted {} stubs.", deletedCount);
  }
}
