package com.filmpire.movie.controller;

import com.filmpire.movie.dto.*;
import com.filmpire.movie.service.MovieService;
import com.filmpire.shared.dto.ApiResponse;
import com.filmpire.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public ResponseEntity<ApiResponse<MovieDto>> getMovieById(
        @Parameter(description = "TMDB movie ID", example = "550")
        @PathVariable("id") Long id
    ) {
        log.info("GET /api/v1/movies/{} - Fetching movie details", id);
        MovieDto movie = movieService.getMovieById(id);
        return ResponseEntity.ok(ApiResponse.success(movie, "Movie retrieved successfully", 200));
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
    public ResponseEntity<ApiResponse<PageResponse<MovieListDto>>> discoverMovies(
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
        PageResponse<MovieListDto> movies = movieService.discoverMovies(page, size, genreId, year, minRating);
        return ResponseEntity.ok(ApiResponse.success(movies, "Movies discovered successfully", 200));
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
    public ResponseEntity<ApiResponse<PageResponse<MovieListDto>>> searchMovies(
        @Parameter(description = "Search query", example = "Inception", required = true)
        @RequestParam(value = "query") String query,
        
        @Parameter(description = "Page number", example = "1")
        @RequestParam(value = "page", defaultValue = "1") int page,
        
        @Parameter(description = "Page size", example = "20")
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/movies/search - query={}, page={}", query, page);
        PageResponse<MovieListDto> movies = movieService.searchMovies(query, page, size);
        return ResponseEntity.ok(ApiResponse.success(movies, "Search completed successfully", 200));
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
    public ResponseEntity<ApiResponse<PageResponse<MovieListDto>>> getTrendingMovies(
        @Parameter(description = "Time window (day or week)", example = "week")
        @RequestParam(value = "timeWindow", defaultValue = "week") String timeWindow,
        
        @Parameter(description = "Page number", example = "1")
        @RequestParam(value = "page", defaultValue = "1") int page,
        
        @Parameter(description = "Page size", example = "20")
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/movies/trending - timeWindow={}, page={}", timeWindow, page);
        PageResponse<MovieListDto> movies = movieService.getTrendingMovies(timeWindow, page, size);
        return ResponseEntity.ok(ApiResponse.success(movies, "Trending movies retrieved successfully", 200));
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
    public ResponseEntity<ApiResponse<PageResponse<MovieListDto>>> getPopularMovies(
        @Parameter(description = "Page number", example = "1")
        @RequestParam(value = "page", defaultValue = "1") int page,
        
        @Parameter(description = "Page size", example = "20")
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/movies/popular - page={}", page);
        PageResponse<MovieListDto> movies = movieService.getPopularMovies(page, size);
        return ResponseEntity.ok(ApiResponse.success(movies, "Popular movies retrieved successfully", 200));
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
    public ResponseEntity<ApiResponse<PageResponse<MovieListDto>>> getTopRatedMovies(
        @Parameter(description = "Page number", example = "1")
        @RequestParam(value = "page", defaultValue = "1") int page,
        
        @Parameter(description = "Page size", example = "20")
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/movies/top-rated - page={}", page);
        PageResponse<MovieListDto> movies = movieService.getTopRatedMovies(page, size);
        return ResponseEntity.ok(ApiResponse.success(movies, "Top-rated movies retrieved successfully", 200));
    }

    /**
     * Get movie videos (trailers, clips).
     *
     * @param id TMDB movie ID
     * @return List of videos
     */
    @GetMapping("/{id}/videos")
    @Operation(summary = "Get movie videos", description = "Get trailers and clips for a movie")
    public ResponseEntity<ApiResponse<List<VideoDto>>> getMovieVideos(
        @Parameter(description = "TMDB movie ID", example = "550")
        @PathVariable("id") Long id
    ) {
        log.info("GET /api/v1/movies/{}/videos - Fetching videos", id);
        List<VideoDto> videos = movieService.getMovieVideos(id);
        return ResponseEntity.ok(ApiResponse.success(videos, "Videos retrieved successfully", 200));
    }

    /**
     * Get movie credits (cast and crew).
     *
     * @param id TMDB movie ID
     * @return Credits
     */
    @GetMapping("/{id}/credits")
    @Operation(summary = "Get movie credits", description = "Get cast and crew information for a movie")
    public ResponseEntity<ApiResponse<CreditsDto>> getMovieCredits(
        @Parameter(description = "TMDB movie ID", example = "550")
        @PathVariable("id") Long id
    ) {
        log.info("GET /api/v1/movies/{}/credits - Fetching credits", id);
        CreditsDto credits = movieService.getMovieCredits(id);
        return ResponseEntity.ok(ApiResponse.success(credits, "Credits retrieved successfully", 200));
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
    public ResponseEntity<ApiResponse<PageResponse<MovieListDto>>> getSimilarMovies(
        @Parameter(description = "TMDB movie ID", example = "550")
        @PathVariable("id") Long id,
        
        @Parameter(description = "Page number", example = "1")
        @RequestParam(value = "page", defaultValue = "1") int page,
        
        @Parameter(description = "Page size", example = "20")
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/movies/{}/similar - page={}", id, page);
        PageResponse<MovieListDto> movies = movieService.getSimilarMovies(id, page, size);
        return ResponseEntity.ok(ApiResponse.success(movies, "Similar movies retrieved successfully", 200));
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
    public ResponseEntity<ApiResponse<PageResponse<MovieListDto>>> getRecommendedMovies(
        @Parameter(description = "TMDB movie ID", example = "550")
        @PathVariable("id") Long id,
        
        @Parameter(description = "Page number", example = "1")
        @RequestParam(value = "page", defaultValue = "1") int page,
        
        @Parameter(description = "Page size", example = "20")
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        log.info("GET /api/v1/movies/{}/recommendations - page={}", id, page);
        PageResponse<MovieListDto> movies = movieService.getRecommendedMovies(id, page, size);
        return ResponseEntity.ok(ApiResponse.success(movies, "Recommendations retrieved successfully", 200));
    }
}

