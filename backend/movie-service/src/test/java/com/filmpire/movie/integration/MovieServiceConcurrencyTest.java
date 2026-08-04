package com.filmpire.movie.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filmpire.movie.client.TmdbClient;
import com.filmpire.movie.client.dto.TmdbMovieListResponse;
import com.filmpire.movie.client.dto.TmdbMovieResponse;
import com.filmpire.movie.model.Movie;
import com.filmpire.movie.repository.MovieRepository;
import com.filmpire.movie.service.MovieService;
import com.filmpire.movie.support.AbstractMongoIntegrationTest;
import com.filmpire.movie.support.TestCacheConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Concurrency and retention integration test for {@link MovieService} (Task #44).
 *
 * <p>Verifies:
 *
 * <ul>
 *   <li>Single-flight protection: N concurrent requests missing the cache on the same key trigger
 *       exactly 1 TMDB client call.
 *   <li>Catalog retention policy: {@link MovieCatalogRetentionService} evicts stale list-sourced
 *       stubs while preserving detail-complete records.
 * </ul>
 */
@SpringBootTest
@Import(TestCacheConfig.class)
@ActiveProfiles("test")
@DisplayName("MovieService Concurrency & Retention Integration Test")
class MovieServiceConcurrencyTest extends AbstractMongoIntegrationTest {

  @Autowired private MovieService movieService;

  @Autowired private MovieRepository movieRepository;

  @MockitoBean private TmdbClient tmdbClient;

  @BeforeEach
  void setUp() {
    movieRepository.deleteAll();
  }

  @AfterEach
  void tearDown() {
    movieRepository.deleteAll();
  }

  /**
   * Proves that N concurrent threads requesting the same uncached movie detail endpoint produce
   * exactly 1 upstream TMDB API call due to single-flight locking.
   *
   * @throws Exception if thread execution or latch countdown fails
   */
  @Test
  @DisplayName("Single-Flight: Concurrent detail misses produce exactly 1 TMDB call")
  void getMovieById_ConcurrentMisses_ShouldSingleFlightUpstreamCall() throws Exception {
    Long tmdbId = 9999L;
    TmdbMovieResponse mockDetail =
        new TmdbMovieResponse(
            tmdbId,
            "Concurrent Detail Test",
            "Concurrent Detail Test",
            "Overview",
            "/poster.jpg",
            "/backdrop.jpg",
            LocalDate.of(2023, Month.JANUARY, 1),
            8.0,
            100,
            List.of(),
            120,
            "Released",
            50000000L,
            100000000L,
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            "en",
            100.0,
            false,
            "tt9999",
            "Tagline",
            "https://homepage.com",
            null,
            null);

    when(tmdbClient.getMovieDetails(any(), any())).thenReturn(mockDetail);

    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      futures.add(
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  movieService.getMovieById(tmdbId);
                } catch (InterruptedException _) {
                  Thread.currentThread().interrupt();
                } finally {
                  doneLatch.countDown();
                }
              }));
    }

    startLatch.countDown(); // Release all threads simultaneously
    boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(completed).isTrue();
    for (Future<?> future : futures) {
      future.get(); // Assert no exceptions thrown
    }

    // Single-flight guarantee: exactly 1 TMDB API call despite 10 concurrent requests
    verify(tmdbClient, times(1)).getMovieDetails(eq(tmdbId), any());
  }

  /**
   * Proves that N concurrent threads requesting the same uncached category list produce exactly 1
   * upstream TMDB API call due to single-flight @Cacheable synchronization.
   *
   * @throws Exception if thread execution fails
   */
  @Test
  @DisplayName("Single-Flight: Concurrent list category misses produce exactly 1 TMDB call")
  void getMovieCategoryRaw_ConcurrentMisses_ShouldSingleFlightUpstreamCall() throws Exception {
    String category = "popular";
    int page = 1;
    TmdbMovieListResponse.TmdbMovieItem item =
        new TmdbMovieListResponse.TmdbMovieItem(
            8888L,
            "Popular Item",
            "Overview",
            "/poster.jpg",
            "/backdrop.jpg",
            "2023-05-01",
            7.5,
            50,
            List.of(28L),
            50.0,
            false,
            "en");
    TmdbMovieListResponse mockListResponse = new TmdbMovieListResponse(page, 1, 1, List.of(item));

    when(tmdbClient.getPopularMovies(any(), anyInt())).thenReturn(mockListResponse);

    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      futures.add(
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  movieService.getMovieCategoryRaw(category, page);
                } catch (InterruptedException _) {
                  Thread.currentThread().interrupt();
                } finally {
                  doneLatch.countDown();
                }
              }));
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(completed).isTrue();
    for (Future<?> future : futures) {
      future.get();
    }

    // Single-flight guarantee: exactly 1 TMDB API call for popular movies list
    verify(tmdbClient, times(1)).getPopularMovies(any(), anyInt());
  }

  /**
   * Proves catalog retention policy deletes stale list-sourced stubs (runtime is null) older than 7
   * days, while leaving detail-complete records (runtime non-null) intact.
   */
  @Test
  @DisplayName("Retention Policy: Purges list stubs older than 7 days, preserves detail entities")
  void catalogRetentionPolicy_ShouldPurgeStaleStubsAndKeepDetailEntities() {
    LocalDateTime oldTimestamp = LocalDateTime.now(ZoneOffset.UTC).minusDays(10);
    LocalDateTime recentTimestamp = LocalDateTime.now(ZoneOffset.UTC).minusDays(2);

    // 1. Old list stub (runtime == null, updated 10 days ago) -> should be purged
    Movie oldListStub =
        Movie.builder()
            .tmdbId(101L)
            .title("Old List Stub")
            .runtime(null)
            .createdAt(oldTimestamp)
            .updatedAt(oldTimestamp)
            .build();

    // 2. Recent list stub (runtime == null, updated 2 days ago) -> should be kept
    Movie recentListStub =
        Movie.builder()
            .tmdbId(102L)
            .title("Recent List Stub")
            .runtime(null)
            .createdAt(recentTimestamp)
            .updatedAt(recentTimestamp)
            .build();

    // 3. Old detail document (runtime == 140, updated 10 days ago) -> should be kept
    Movie oldDetailDoc =
        Movie.builder()
            .tmdbId(103L)
            .title("Old Detail Doc")
            .runtime(140)
            .createdAt(oldTimestamp)
            .updatedAt(oldTimestamp)
            .build();

    movieRepository.saveAll(List.of(oldListStub, recentListStub, oldDetailDoc));

    // Act: execute retention cleanup
    long evicted = movieService.cleanupListSourcedStubs(7);

    // Assert
    assertThat(evicted).isEqualTo(1);
    assertThat(movieRepository.findByTmdbId(101L)).isEmpty();
    assertThat(movieRepository.findByTmdbId(102L)).isPresent();
    assertThat(movieRepository.findByTmdbId(103L)).isPresent();
  }
}
