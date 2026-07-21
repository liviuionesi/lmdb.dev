package com.filmpire.movie.service;

import com.filmpire.movie.client.TmdbClient;
import com.filmpire.movie.client.dto.TmdbMovieResponse;
import com.filmpire.movie.dto.MovieDto;
import com.filmpire.movie.mapper.MovieMapper;
import com.filmpire.movie.model.Movie;
import com.filmpire.movie.repository.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for the native {@link MovieService}'s hybrid-caching behavior
 * (MongoDB long-term + TMDB fallback; Redis is applied by the {@code
 * @Cacheable} layer, not exercised here).
 *
 * <p>All collaborators are mocked, so these tests assert the CALL PATTERN
 * rather than data: how many times MongoDB is queried, whether TMDB is called,
 * and when a save happens. That's the real contract — the point of the cache is
 * to minimize TMDB calls, so the verify(...) counts ARE the behavior under
 * test. The double-check-inside-lock pattern (the service's single-flight
 * guard) is why some tests stub {@code findByTmdbId} to return different values
 * across successive calls.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MovieService Cache Tests")
class MovieServiceCacheTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private TmdbClient tmdbClient;

    @Mock
    private MovieMapper movieMapper;

    @InjectMocks
    private MovieService movieService;

    private final String tmdbApiKey = "test-api-key";

    /** Injects the @Value-bound TMDB key that isn't populated without a Spring
     *  context. */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(movieService, "tmdbApiKey", tmdbApiKey);
    }

    /**
     * The cache-hit fast path: when the movie is already in MongoDB, TMDB must
     * NOT be called at all ({@code verifyNoInteractions(tmdbClient)}) — this is
     * the primary way the service stays under TMDB's rate limit.
     */
    @Test
    @DisplayName("Should hit MongoDB cache before calling TMDB API")
    void getMovieById_WhenInCache_ShouldNotCallAPI() {
        // Arrange
        Long tmdbId = 550L;
        Movie cachedMovie = createTestMovie(tmdbId);
        MovieDto movieDto = createTestMovieDto(tmdbId);

        when(movieRepository.findByTmdbId(tmdbId)).thenReturn(Optional.of(cachedMovie));
        when(movieMapper.toDto(cachedMovie)).thenReturn(movieDto);

        // Act
        MovieDto result = movieService.getMovieById(tmdbId);

        // Assert
        assertThat(result).isNotNull();
        verify(movieRepository, times(1)).findByTmdbId(tmdbId);
        verify(movieMapper, times(1)).toDto(cachedMovie);
        verifyNoInteractions(tmdbClient);
    }

    /**
     * The cache-miss path: with the movie absent, the service must call TMDB
     * once and persist the result. The two findByTmdbId calls reflect the
     * double-check around the single-flight lock (check, lock, re-check).
     */
    @Test
    @DisplayName("Should fetch from API and cache when not in MongoDB")
    void getMovieById_WhenNotInCache_ShouldFetchAndCache() {
        // Arrange
        Long tmdbId = 550L;
        TmdbMovieResponse tmdbResponse = createTestTmdbMovieResponse(tmdbId);
        Movie savedMovie = createTestMovie(tmdbId);
        MovieDto movieDto = createTestMovieDto(tmdbId);

        when(movieRepository.findByTmdbId(tmdbId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(tmdbClient.getMovieDetails(tmdbId, tmdbApiKey)).thenReturn(tmdbResponse);
        when(movieRepository.save(any(Movie.class))).thenReturn(savedMovie);
        when(movieMapper.toDto(savedMovie)).thenReturn(movieDto);

        // Act
        MovieDto result = movieService.getMovieById(tmdbId);

        // Assert
        assertThat(result).isNotNull();
        verify(movieRepository, times(2)).findByTmdbId(tmdbId);
        verify(tmdbClient, times(1)).getMovieDetails(tmdbId, tmdbApiKey);
        verify(movieRepository, times(1)).save(any(Movie.class));
        verify(movieMapper, times(1)).toDto(savedMovie);
    }

    /**
     * Repeated reads of a cached movie must each hit MongoDB but never TMDB —
     * ten calls produce ten DB lookups and zero API calls, confirming the cache
     * absorbs read volume without amplifying it onto the rate-limited upstream.
     * (Sequential here; true concurrency is out of scope for a mock test.)
     */
    @Test
    @DisplayName("Should handle multiple concurrent requests efficiently")
    void getMovieById_ConcurrentRequests_ShouldHandleEfficiently() {
        // Arrange
        Long tmdbId = 550L;
        Movie cachedMovie = createTestMovie(tmdbId);
        MovieDto movieDto = createTestMovieDto(tmdbId);

        when(movieRepository.findByTmdbId(tmdbId)).thenReturn(Optional.of(cachedMovie));
        when(movieMapper.toDto(cachedMovie)).thenReturn(movieDto);

        // Act - Simulate multiple calls
        for (int i = 0; i < 10; i++) {
            MovieDto result = movieService.getMovieById(tmdbId);
            assertThat(result).isNotNull();
        }

        // Assert - Should query MongoDB 10 times but never hit TMDB API
        verify(movieRepository, times(10)).findByTmdbId(tmdbId);
        verifyNoInteractions(tmdbClient);
    }

    /**
     * The save-then-serve sequence: after a first call fetches and persists,
     * a second call must be served from MongoDB — so across both calls TMDB is
     * hit exactly once and save happens once. The staged findByTmdbId stubs
     * (empty, empty, present) model check→lock-recheck→subsequent-hit.
     */
    @Test
    @DisplayName("Should cache save data from TMDB API")
    void getMovieById_AfterAPICall_ShouldBeCached() {
        // Arrange
        Long tmdbId = 550L;
        TmdbMovieResponse tmdbResponse = createTestTmdbMovieResponse(tmdbId);
        Movie savedMovie = createTestMovie(tmdbId);
        MovieDto movieDto = createTestMovieDto(tmdbId);

        // First call - not in cache
        when(movieRepository.findByTmdbId(tmdbId))
                .thenReturn(Optional.empty()) // 1. First call (outside lock) -> Empty
                .thenReturn(Optional.empty()) // 2. Second call (inside lock) -> Empty -> Call API
                .thenReturn(Optional.of(savedMovie)); // 3. Second service call -> Found
        when(tmdbClient.getMovieDetails(tmdbId, tmdbApiKey)).thenReturn(tmdbResponse);
        when(movieRepository.save(any(Movie.class))).thenReturn(savedMovie);
        when(movieMapper.toDto(savedMovie)).thenReturn(movieDto);

        // Act
        MovieDto firstCall = movieService.getMovieById(tmdbId);
        MovieDto secondCall = movieService.getMovieById(tmdbId);

        // Assert
        assertThat(firstCall).isNotNull();
        assertThat(secondCall).isNotNull();
        verify(movieRepository, times(3)).findByTmdbId(tmdbId);
        verify(tmdbClient, times(1)).getMovieDetails(tmdbId, tmdbApiKey); // Only once
        verify(movieRepository, times(1)).save(any(Movie.class));
    }

    // Helper methods

    /**
     * Builds a fully-populated Movie entity for cache-hit stubbing.
     *
     * @param tmdbId TMDB id to embed
     * @return a test movie
     */
    private Movie createTestMovie(Long tmdbId) {
        return Movie.builder()
                .id("mongo123")
                .tmdbId(tmdbId)
                .title("Fight Club")
                .overview("An insomniac office worker...")
                .posterPath("/poster.jpg")
                .releaseDate(LocalDate.of(1999, 10, 15))
                .voteAverage(8.4)
                .voteCount(25000)
                .runtime(139)
                .status("Released")
                .budget(63000000L)
                .revenue(100853753L)
                .popularity(450.5)
                .adult(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Builds the DTO the mapper is stubbed to return.
     *
     * @param tmdbId TMDB id to embed
     * @return a test DTO
     */
    private MovieDto createTestMovieDto(Long tmdbId) {
        return MovieDto.builder()
                .id("mongo123")
                .tmdbId(tmdbId)
                .title("Fight Club")
                .voteAverage(8.4)
                .runtime(139)
                .build();
    }

    /**
     * Builds the raw TMDB response the client is stubbed to return on a miss.
     *
     * @param tmdbId TMDB id to embed
     * @return a test TMDB response
     */
    private TmdbMovieResponse createTestTmdbMovieResponse(Long tmdbId) {
        return new TmdbMovieResponse(
            tmdbId,
            "Fight Club",
            "An insomniac office worker...",
            "/poster.jpg",
            null,
            LocalDate.of(1999, 10, 15),
            8.4,
            25000,
            null,
            139,
            "Released",
            63000000L,
            100853753L,
            null,
            null,
            "en",
            450.5,
            false,
            null,
            null,
            null
        );
    }
}

