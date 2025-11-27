package com.filmpire.movie.controller;

import com.filmpire.movie.dto.*;
import com.filmpire.movie.service.MovieService;
import com.filmpire.shared.dto.PageResponse;
import com.filmpire.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for movie operations.
 * All endpoints are prefixed with /api/v1/movies.
 */
@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Movie API", description = "Endpoints for movie discovery, search, and details")
public class MovieController {

    private final MovieService movieService;

    /**
     * Get movie details by TMDB ID.
     *
     * @param id TMDB movie ID
     * @return Movie details
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get movie by ID", description = "Retrieve detailed movie information by TMDB ID")
    public ResponseEntity<MovieDto> getMovieById(
        @Parameter(description = "TMDB movie ID", example = "550")
        @PathVariable("id") Long id
    ) {
        log.info("GET /api/v1/movies/{} - Fetching movie details", id);
        return ResponseEntity.ok(movieService.getMovieById(id));
    }

    /**
     * Discover movies with filters.
     *
     * @param page Page number (1-based)
     * @param size Page size
     * @param genreId Genre ID filter
     * @param year Release year filter
     * @param minRating Minimum rating filter
     * @return Page of movies
     */
    @GetMapping("/discover")
    @Operation(summary = "Discover movies", description = "Discover movies with various filters")
    public ResponseEntity<PageResponse<MovieListDto>> discoverMovies(
        @Parameter(description = "Page number", example = "1")
        @RequestParam(value = "page", defaultValue = "1") int page,
        
        @Parameter(description = "Page size", example = "20")
        @RequestParam(value = "size", defaultValue = "20") int size,
        
        @Parameter(description = "Genre ID filter", example = "28")
        @RequestParam(value = "genreId", required = false) Long genreId,
        
        @Parameter(description = "Release year filter", example = "2024")
        @RequestParam(value = "year", required = false) Integer year,
        
        @Parameter(description = "Minimum rating filter", example = "7.0")
        @RequestParam(value = "minRating", required = false) Double minRating
    ) {
        log.info("GET /api/v1/movies/discover - page={}, size={}, genre={}, year={}, minRating={}", 
                 page, size, genreId, year, minRating);
        return ResponseEntity.ok(movieService.discoverMovies(page, size, genreId, year, minRating));
    }

    /**
     * Search movies by query.
     *
     * @param query Search query
     * @param page Page number (1-based)
     * @param size Page size
     * @return Page of movies
     */
    @GetMapping("/search")
    @Operation(summary = "Search movies", description = "Search movies by title or keywords")
    public ResponseEntity<PageResponse<MovieListDto>> searchMovies(
        @Parameter(description = "Search query", example = "Inception", required = true)
        @RequestParam(value = "query") String query,
        
        @Parameter(description = "Page number", example = "1")
        @RequestParam(value = "page", defaultValue = "1") int page,
        
        @Parameter(description = "Page size", example = "20")
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/movies/search - query={}, page={}", query, page);
        return ResponseEntity.ok(movieService.searchMovies(query, page, size));
    }

    /**
     * Get trending movies.
     *
     * @param timeWindow Time window (day or week)
     * @param page Page number (1-based)
     * @param size Page size
     * @return Page of trending movies
     */
    @GetMapping("/trending")
    @Operation(summary = "Get trending movies", description = "Get trending movies for the day or week")
    public ResponseEntity<PageResponse<MovieListDto>> getTrendingMovies(
        @Parameter(description = "Time window (day or week)", example = "week")
        @RequestParam(value = "timeWindow", defaultValue = "week") String timeWindow,
        
        @Parameter(description = "Page number", example = "1")
        @RequestParam(value = "page", defaultValue = "1") int page,
        
        @Parameter(description = "Page size", example = "20")
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/movies/trending - timeWindow={}, page={}", timeWindow, page);
        return ResponseEntity.ok(movieService.getTrendingMovies(timeWindow, page, size));
    }

    /**
     * Get popular movies.
     *
     * @param page Page number (1-based)
     * @param size Page size
     * @return Page of popular movies
     */
    @GetMapping("/popular")
    @Operation(summary = "Get popular movies", description = "Get currently popular movies")
    public ResponseEntity<PageResponse<MovieListDto>> getPopularMovies(
        @Parameter(description = "Page number", example = "1")
        @RequestParam(value = "page", defaultValue = "1") int page,
        
        @Parameter(description = "Page size", example = "20")
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/movies/popular - page={}", page);
        return ResponseEntity.ok(movieService.getPopularMovies(page, size));
    }

    /**
     * Get top-rated movies.
     *
     * @param page Page number (1-based)
     * @param size Page size
     * @return Page of top-rated movies
     */
    @GetMapping("/top-rated")
    @Operation(summary = "Get top-rated movies", description = "Get highest-rated movies of all time")
    public ResponseEntity<PageResponse<MovieListDto>> getTopRatedMovies(
        @Parameter(description = "Page number", example = "1")
        @RequestParam(value = "page", defaultValue = "1") int page,
        
        @Parameter(description = "Page size", example = "20")
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/movies/top-rated - page={}", page);
        return ResponseEntity.ok(movieService.getTopRatedMovies(page, size));
    }

    /**
     * Get movie videos (trailers, clips).
     *
     * @param id TMDB movie ID
     * @return List of videos
     */
    @GetMapping("/{id}/videos")
    @Operation(summary = "Get movie videos", description = "Get trailers and clips for a movie")
    public ResponseEntity<List<VideoDto>> getMovieVideos(
        @Parameter(description = "TMDB movie ID", example = "550")
        @PathVariable("id") Long id
    ) {
        log.info("GET /api/v1/movies/{}/videos - Fetching videos", id);
        return ResponseEntity.ok(movieService.getMovieVideos(id));
    }

    /**
     * Get movie credits (cast and crew).
     *
     * @param id TMDB movie ID
     * @return Credits
     */
    @GetMapping("/{id}/credits")
    @Operation(summary = "Get movie credits", description = "Get cast and crew information for a movie")
    public ResponseEntity<CreditsDto> getMovieCredits(
        @Parameter(description = "TMDB movie ID", example = "550")
        @PathVariable("id") Long id
    ) {
        log.info("GET /api/v1/movies/{}/credits - Fetching credits", id);
        return ResponseEntity.ok(movieService.getMovieCredits(id));
    }

    /**
     * Get similar movies.
     *
     * @param id TMDB movie ID
     * @param page Page number (1-based)
     * @param size Page size
     * @return Page of similar movies
     */
    @GetMapping("/{id}/similar")
    @Operation(summary = "Get similar movies", description = "Get movies similar to the specified movie")
    public ResponseEntity<PageResponse<MovieListDto>> getSimilarMovies(
        @Parameter(description = "TMDB movie ID", example = "550")
        @PathVariable("id") Long id,
        
        @Parameter(description = "Page number", example = "1")
        @RequestParam(value = "page", defaultValue = "1") int page,
        
        @Parameter(description = "Page size", example = "20")
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/movies/{}/similar - page={}", id, page);
        return ResponseEntity.ok(movieService.getSimilarMovies(id, page, size));
    }

    /**
     * Get recommended movies.
     *
     * @param id TMDB movie ID
     * @param page Page number (1-based)
     * @param size Page size
     * @return Page of recommended movies
     */
    @GetMapping("/{id}/recommendations")
    @Operation(summary = "Get movie recommendations", description = "Get recommended movies based on the specified movie")
    public ResponseEntity<PageResponse<MovieListDto>> getRecommendedMovies(
        @Parameter(description = "TMDB movie ID", example = "550")
        @PathVariable("id") Long id,
        
        @Parameter(description = "Page number", example = "1")
        @RequestParam(value = "page", defaultValue = "1") int page,
        
        @Parameter(description = "Page size", example = "20")
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/movies/{}/recommendations - page={}", id, page);
        return ResponseEntity.ok(movieService.getRecommendedMovies(id, page, size));
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
