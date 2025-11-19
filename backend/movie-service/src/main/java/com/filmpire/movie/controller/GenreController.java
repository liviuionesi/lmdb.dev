package com.filmpire.movie.controller;

import com.filmpire.movie.dto.GenreDto;
import com.filmpire.movie.service.MovieService;
import com.filmpire.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for genre operations.
 */
@RestController
@RequestMapping("/api/v1/genres")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Genre API", description = "Endpoints for movie genres")
public class GenreController {

    private final MovieService movieService;

    /**
     * Get all movie genres.
     *
     * @return List of genres
     */
    @GetMapping
    @Operation(summary = "Get all genres", description = "Retrieve all available movie genres")
    public ResponseEntity<ApiResponse<List<GenreDto>>> getAllGenres() {
        log.info("GET /api/v1/genres - Fetching all genres");
        List<GenreDto> genres = movieService.getAllGenres();
        return ResponseEntity.ok(ApiResponse.success(genres, "Genres retrieved successfully", 200));
    }
}

