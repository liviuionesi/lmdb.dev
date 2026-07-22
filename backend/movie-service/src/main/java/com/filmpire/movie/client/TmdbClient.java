package com.filmpire.movie.client;

import com.filmpire.movie.client.dto.*;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Http Client for TMDB API.
 * Base URL and API key are configured in application.yml.
 */
@HttpExchange
public interface TmdbClient {

    /**
     * Get movie details by ID.
     *
     * @param movieId TMDB movie ID
     * @param apiKey API key
     * @return Movie details
     */
    @GetExchange("/movie/{movieId}")
    TmdbMovieResponse getMovieDetails(
        @PathVariable("movieId") Long movieId,
        @RequestParam("api_key") String apiKey
    );

    /**
     * Discover movies with filters.
     *
     * @param apiKey API key
     * @param page Page number
     * @param sortBy Sort by
     * @param genreId Genre ID filter
     * @param year Release year filter
     * @param minRating Minimum rating filter
     * @param castId Cast member TMDB person ID filter (TMDB's {@code with_cast})
     * @return List of movies
     */
    @GetExchange("/discover/movie")
    TmdbMovieListResponse discoverMovies(
        @RequestParam("api_key") String apiKey,
        @RequestParam(value = "page", defaultValue = "1") Integer page,
        @RequestParam(value = "sort_by", defaultValue = "popularity.desc") String sortBy,
        @RequestParam(value = "with_genres", required = false) Long genreId,
        @RequestParam(value = "year", required = false) Integer year,
        @RequestParam(value = "vote_average.gte", required = false) Double minRating,
        @RequestParam(value = "with_cast", required = false) Long castId
    );

    /**
     * Search movies by query.
     *
     * @param apiKey API key
     * @param query Search query
     * @param page Page number
     * @return List of movies
     */
    @GetExchange("/search/movie")
    TmdbMovieListResponse searchMovies(
        @RequestParam("api_key") String apiKey,
        @RequestParam("query") String query,
        @RequestParam(value = "page", defaultValue = "1") Integer page
    );

    /**
     * Get trending movies.
     *
     * @param timeWindow Time window (day or week)
     * @param apiKey API key
     * @param page Page number
     * @return List of trending movies
     */
    @GetExchange("/trending/movie/{timeWindow}")
    TmdbMovieListResponse getTrendingMovies(
        @PathVariable("timeWindow") String timeWindow,
        @RequestParam("api_key") String apiKey,
        @RequestParam(value = "page", defaultValue = "1") Integer page
    );

    /**
     * Get popular movies.
     *
     * @param apiKey API key
     * @param page Page number
     * @return List of popular movies
     */
    @GetExchange("/movie/popular")
    TmdbMovieListResponse getPopularMovies(
        @RequestParam("api_key") String apiKey,
        @RequestParam(value = "page", defaultValue = "1") Integer page
    );

    /**
     * Get top-rated movies.
     *
     * @param apiKey API key
     * @param page Page number
     * @return List of top-rated movies
     */
    @GetExchange("/movie/top_rated")
    TmdbMovieListResponse getTopRatedMovies(
        @RequestParam("api_key") String apiKey,
        @RequestParam(value = "page", defaultValue = "1") Integer page
    );

    /**
     * Get upcoming movie releases.
     *
     * @param apiKey API key
     * @param page Page number
     * @return List of upcoming movies
     */
    @GetExchange("/movie/upcoming")
    TmdbMovieListResponse getUpcomingMovies(
        @RequestParam("api_key") String apiKey,
        @RequestParam(value = "page", defaultValue = "1") Integer page
    );

    /**
     * Get movies currently in theaters.
     *
     * @param apiKey API key
     * @param page Page number
     * @return List of now-playing movies
     */
    @GetExchange("/movie/now_playing")
    TmdbMovieListResponse getNowPlayingMovies(
        @RequestParam("api_key") String apiKey,
        @RequestParam(value = "page", defaultValue = "1") Integer page
    );

    /**
     * Get movie videos (trailers, clips).
     *
     * @param movieId TMDB movie ID
     * @param apiKey API key
     * @return List of videos
     */
    @GetExchange("/movie/{movieId}/videos")
    TmdbVideosResponse getMovieVideos(
        @PathVariable("movieId") Long movieId,
        @RequestParam("api_key") String apiKey
    );

    /**
     * Get movie credits (cast and crew).
     *
     * @param movieId TMDB movie ID
     * @param apiKey API key
     * @return Credits
     */
    @GetExchange("/movie/{movieId}/credits")
    TmdbCreditsResponse getMovieCredits(
        @PathVariable("movieId") Long movieId,
        @RequestParam("api_key") String apiKey
    );

    /**
     * Get similar movies.
     *
     * @param movieId TMDB movie ID
     * @param apiKey API key
     * @param page Page number
     * @return List of similar movies
     */
    @GetExchange("/movie/{movieId}/similar")
    TmdbMovieListResponse getSimilarMovies(
        @PathVariable("movieId") Long movieId,
        @RequestParam("api_key") String apiKey,
        @RequestParam(value = "page", defaultValue = "1") Integer page
    );

    /**
     * Get recommended movies.
     *
     * @param movieId TMDB movie ID
     * @param apiKey API key
     * @param page Page number
     * @return List of recommended movies
     */
    @GetExchange("/movie/{movieId}/recommendations")
    TmdbMovieListResponse getRecommendedMovies(
        @PathVariable("movieId") Long movieId,
        @RequestParam("api_key") String apiKey,
        @RequestParam(value = "page", defaultValue = "1") Integer page
    );

    /**
     * Get all genres.
     *
     * @param apiKey API key
     * @return List of genres
     */
    @GetExchange("/genre/movie/list")
    TmdbGenresResponse getGenres(
        @RequestParam("api_key") String apiKey
    );
}
