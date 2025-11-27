package com.filmpire.movie.controller;

import com.filmpire.movie.dto.GenreDto;
import com.filmpire.movie.service.MovieService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for GenreController.
 */
@WebMvcTest(GenreController.class)
@DisplayName("GenreController Tests")
class GenreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MovieService movieService;

    @Test
    @DisplayName("GET /api/v1/genres - Should return all genres")
    void getAllGenres_ShouldReturnAllGenres() throws Exception {
        // Arrange
        List<GenreDto> genres = createTestGenres();
        when(movieService.getAllGenres()).thenReturn(genres);

        // Act & Assert
        mockMvc.perform(get("/api/v1/genres")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].id").value(28))
                .andExpect(jsonPath("$[0].name").value("Action"))
                .andExpect(jsonPath("$[1].id").value(12))
                .andExpect(jsonPath("$[1].name").value("Adventure"))
                .andExpect(jsonPath("$[2].id").value(16))
                .andExpect(jsonPath("$[2].name").value("Animation"));
    }

    @Test
    @DisplayName("GET /api/v1/genres - Should return empty list when no genres")
    void getAllGenres_WhenNoGenres_ShouldReturnEmptyList() throws Exception {
        // Arrange
        when(movieService.getAllGenres()).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/v1/genres")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private List<GenreDto> createTestGenres() {
        return Arrays.asList(
                GenreDto.builder().id(28L).name("Action").build(),
                GenreDto.builder().id(12L).name("Adventure").build(),
                GenreDto.builder().id(16L).name("Animation").build(),
                GenreDto.builder().id(35L).name("Comedy").build(),
                GenreDto.builder().id(18L).name("Drama").build()
        );
    }
}
