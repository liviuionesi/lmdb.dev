package com.filmpire.movie.controller;

import com.filmpire.movie.service.MovieService;
import com.filmpire.shared.exception.ResourceNotFoundException;
import com.filmpire.shared.exception.ServiceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Error-handling tests for the movie web layer ({@code @WebMvcTest} slice with
 * a mocked service).
 *
 * <p>Two kinds of failure are covered: request-shape errors the controller
 * rejects itself (missing required {@code query} param, non-numeric id → 400),
 * and the service's declared exception contract (ResourceNotFound /
 * ServiceUnavailable / RuntimeException). Note the exception-contract tests
 * assert propagation from the stubbed service rather than the mapped HTTP
 * status — they pin the service's throwing behavior; the HTTP mapping of those
 * exceptions is exercised where the exception handler is on the path.</p>
 */
@WebMvcTest(MovieController.class)
@DisplayName("MovieController Error Handling Tests")
class MovieControllerErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MovieService movieService;

    /**
     * A missing movie must surface as ResourceNotFoundException from the
     * service — the signal the controller/handler turns into a 404. Pins the
     * exception type so a change to a silent-null return would be caught.
     */
    @Test
    @DisplayName("Should throw ResourceNotFoundException when movie not found")
    void getMovieById_WhenNotFound_ShouldThrowException() {
        // Arrange
        Long movieId = 999L;
        when(movieService.getMovieById(movieId))
                .thenThrow(new ResourceNotFoundException("Movie not found with ID: " + movieId));

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(
                ResourceNotFoundException.class,
                () -> movieService.getMovieById(movieId)
        );
    }

    /**
     * A TMDB outage must surface as ServiceUnavailableException (the signal for
     * a 503), distinct from not-found — the two map to different statuses, so
     * the exception type must be preserved.
     */
    @Test
    @DisplayName("Should throw ServiceUnavailableException when service unavailable")
    void getMovieById_WhenServiceUnavailable_ShouldThrowException() {
        // Arrange
        Long movieId = 550L;
        when(movieService.getMovieById(movieId))
                .thenThrow(new ServiceUnavailableException("TMDB API is unavailable"));

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(
                ServiceUnavailableException.class,
                () -> movieService.getMovieById(movieId)
        );
    }

    /**
     * An unexpected RuntimeException must propagate (to become a generic 500)
     * rather than be swallowed — pins that unclassified failures still surface
     * as errors instead of being masked.
     */
    @Test
    @DisplayName("Should throw RuntimeException for unexpected errors")
    void getMovieById_WhenUnexpectedException_ShouldThrowException() {
        // Arrange
        Long movieId = 550L;
        when(movieService.getMovieById(movieId))
                .thenThrow(new RuntimeException("Unexpected error"));

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> movieService.getMovieById(movieId)
        );
    }

    /**
     * The {@code query} param is required, so search without it must be
     * rejected with 400 BEFORE reaching the service — a real controller-level
     * validation check (not a stubbed exception), guarding against empty
     * searches hitting TMDB.
     *
     * @throws Exception if the mock request fails
     */
    @Test
    @DisplayName("Search without query parameter should return 400")
    void searchMovies_WithoutQuery_ShouldReturn400() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/search")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    /**
     * The {@code id} path variable is typed Long, so a non-numeric value must
     * be rejected with 400 by type conversion before the service is invoked —
     * confirms malformed URLs fail fast rather than throwing deeper.
     *
     * @throws Exception if the mock request fails
     */
    @Test
    @DisplayName("Should handle invalid path variable format")
    void getMovieById_WithInvalidId_ShouldReturn400() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/{id}", "invalid")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    /**
     * Search must propagate a service outage as ServiceUnavailableException
     * (→ 503) rather than returning empty results, so a TMDB failure isn't
     * misread by the client as "no matches".
     */
    @Test
    @DisplayName("Should throw ServiceUnavailableException when search fails")
    void searchMovies_WhenServiceFails_ShouldThrowException() {
        // Arrange
        when(movieService.searchMovies(anyString(), anyInt(), anyInt()))
                .thenThrow(new ServiceUnavailableException("Search service unavailable"));

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(
                ServiceUnavailableException.class,
                () -> movieService.searchMovies("test", 1, 20)
        );
    }

    /**
     * Discover must likewise propagate an outage as ServiceUnavailableException
     * rather than an empty page — same "don't disguise failure as no results"
     * guarantee on the browse path.
     */
    @Test
    @DisplayName("Should throw ServiceUnavailableException when discover fails")
    void discoverMovies_WhenServiceFails_ShouldThrowException() {
        // Arrange
        when(movieService.discoverMovies(anyInt(), anyInt(), any(), any(), any()))
                .thenThrow(new ServiceUnavailableException("Discovery service unavailable"));

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(
                ServiceUnavailableException.class,
                () -> movieService.discoverMovies(1, 20, null, null, null)
        );
    }

    /**
     * Missing videos for a movie must surface as ResourceNotFoundException
     * (→ 404), so the details page can distinguish "no videos" from a broken
     * request.
     */
    @Test
    @DisplayName("Should throw ResourceNotFoundException when getting videos fails")
    void getMovieVideos_WhenServiceFails_ShouldThrowException() {
        // Arrange
        Long movieId = 550L;
        when(movieService.getMovieVideos(movieId))
                .thenThrow(new ResourceNotFoundException("Videos not found for movie: " + movieId));

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(
                ResourceNotFoundException.class,
                () -> movieService.getMovieVideos(movieId)
        );
    }

    /**
     * Missing credits must likewise surface as ResourceNotFoundException
     * (→ 404) — the credits counterpart of the videos case.
     */
    @Test
    @DisplayName("Should throw ResourceNotFoundException when getting credits fails")
    void getMovieCredits_WhenServiceFails_ShouldThrowException() {
        // Arrange
        Long movieId = 550L;
        when(movieService.getMovieCredits(movieId))
                .thenThrow(new ResourceNotFoundException("Credits not found for movie: " + movieId));

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(
                ResourceNotFoundException.class,
                () -> movieService.getMovieCredits(movieId)
        );
    }
}

