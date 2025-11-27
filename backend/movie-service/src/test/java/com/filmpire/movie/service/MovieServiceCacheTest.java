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
 * Tests for MovieService caching behavior.
 * Verifies hybrid caching strategy (MongoDB + Redis).
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

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(movieService, "tmdbApiKey", tmdbApiKey);
    }

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

    private MovieDto createTestMovieDto(Long tmdbId) {
        return MovieDto.builder()
                .id("mongo123")
                .tmdbId(tmdbId)
                .title("Fight Club")
                .voteAverage(8.4)
                .runtime(139)
                .build();
    }

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

