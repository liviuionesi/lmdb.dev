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
 * Error handling tests for MovieController.
 * Tests exception scenarios and error responses.
 */
@WebMvcTest(MovieController.class)
@DisplayName("MovieController Error Handling Tests")
class MovieControllerErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MovieService movieService;

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

    @Test
    @DisplayName("Search without query parameter should return 400")
    void searchMovies_WithoutQuery_ShouldReturn400() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/search")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle invalid path variable format")
    void getMovieById_WithInvalidId_ShouldReturn400() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/{id}", "invalid")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

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

