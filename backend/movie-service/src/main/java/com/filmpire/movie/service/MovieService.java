package com.filmpire.movie.service;

import com.filmpire.movie.client.TmdbClient;
import com.filmpire.movie.client.dto.*;
import com.filmpire.movie.dto.*;
import com.filmpire.movie.mapper.MovieMapper;
import com.filmpire.movie.model.*;
import com.filmpire.movie.repository.MovieRepository;
import com.filmpire.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Service layer for Movie operations.
 * Implements hybrid caching strategy: Redis (short-term) + MongoDB (long-term) + TMDB API (source of truth).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final TmdbClient tmdbClient;
    private final MovieMapper movieMapper;

    @Value("${tmdb.api.key}")
    private String tmdbApiKey;

    /**
     * Get movie by ID with hybrid caching.
     * 1. Check Redis cache (via @Cacheable)
     * 2. Check MongoDB
     * 3. Fetch from TMDB API and store in MongoDB + Redis
     *
     * @param tmdbId TMDB movie ID
     * @return Movie DTO
     */
    @Cacheable(value = "movies", key = "#tmdbId")
    public MovieDto getMovieById(Long tmdbId) {
        log.info("Fetching movie with TMDB ID: {}", tmdbId);

        // Check MongoDB
        return movieRepository.findByTmdbId(tmdbId)
            .map(movie -> {
                log.info("Movie found in MongoDB: {}", tmdbId);
                return movieMapper.toDto(movie);
            })
            .orElseGet(() -> {
                // Fetch from TMDB API
                log.info("Movie not in MongoDB, fetching from TMDB: {}", tmdbId);
                TmdbMovieResponse tmdbMovie = tmdbClient.getMovieDetails(tmdbId, tmdbApiKey);
                Movie movie = convertAndSaveMovie(tmdbMovie);
                return movieMapper.toDto(movie);
            });
    }

    /**
     * Discover movies with filters.
     *
     * @param page Page number
     * @param size Page size
     * @param genreId Genre ID filter
     * @param year Release year filter
     * @param minRating Minimum rating filter
     * @return Page of movies
     */
    @Cacheable(value = "movieLists", key = "'discover-' + #page + '-' + #size + '-' + #genreId + '-' + #year + '-' + #minRating")
    public PageResponse<MovieListDto> discoverMovies(int page, int size, Long genreId, Integer year, Double minRating) {
        log.info("Discovering movies: page={}, size={}, genre={}, year={}, minRating={}", 
                 page, size, genreId, year, minRating);

        TmdbMovieListResponse response = tmdbClient.discoverMovies(
            tmdbApiKey, page, "popularity.desc", genreId, year, minRating
        );

        List<MovieListDto> movies = response.getResults().stream()
            .map(this::convertTmdbItemToListDto)
            .toList();

        return PageResponse.of(movies, page - 1, size, (long) response.getTotalResults());
    }

    /**
     * Search movies by query.
     *
     * @param query Search query
     * @param page Page number
     * @param size Page size
     * @return Page of movies
     */
    @Cacheable(value = "movieLists", key = "'search-' + #query + '-' + #page")
    public PageResponse<MovieListDto> searchMovies(String query, int page, int size) {
        log.info("Searching movies: query={}, page={}", query, page);

        TmdbMovieListResponse response = tmdbClient.searchMovies(tmdbApiKey, query, page);

        List<MovieListDto> movies = response.getResults().stream()
            .map(this::convertTmdbItemToListDto)
            .toList();

        return PageResponse.of(movies, page - 1, size, (long) response.getTotalResults());
    }

    /**
     * Get trending movies.
     *
     * @param timeWindow Time window (day or week)
     * @param page Page number
     * @param size Page size
     * @return Page of movies
     */
    @Cacheable(value = "movieLists", key = "'trending-' + #timeWindow + '-' + #page")
    public PageResponse<MovieListDto> getTrendingMovies(String timeWindow, int page, int size) {
        log.info("Fetching trending movies: timeWindow={}, page={}", timeWindow, page);

        TmdbMovieListResponse response = tmdbClient.getTrendingMovies(timeWindow, tmdbApiKey, page);

        List<MovieListDto> movies = response.getResults().stream()
            .map(this::convertTmdbItemToListDto)
            .toList();

        return PageResponse.of(movies, page - 1, size, (long) response.getTotalResults());
    }

    /**
     * Get popular movies.
     *
     * @param page Page number
     * @param size Page size
     * @return Page of movies
     */
    @Cacheable(value = "movieLists", key = "'popular-' + #page")
    public PageResponse<MovieListDto> getPopularMovies(int page, int size) {
        log.info("Fetching popular movies: page={}", page);

        TmdbMovieListResponse response = tmdbClient.getPopularMovies(tmdbApiKey, page);

        List<MovieListDto> movies = response.getResults().stream()
            .map(this::convertTmdbItemToListDto)
            .toList();

        return PageResponse.of(movies, page - 1, size, (long) response.getTotalResults());
    }

    /**
     * Get top-rated movies.
     *
     * @param page Page number
     * @param size Page size
     * @return Page of movies
     */
    @Cacheable(value = "movieLists", key = "'toprated-' + #page")
    public PageResponse<MovieListDto> getTopRatedMovies(int page, int size) {
        log.info("Fetching top-rated movies: page={}", page);

        TmdbMovieListResponse response = tmdbClient.getTopRatedMovies(tmdbApiKey, page);

        List<MovieListDto> movies = response.getResults().stream()
            .map(this::convertTmdbItemToListDto)
            .toList();

        return PageResponse.of(movies, page - 1, size, (long) response.getTotalResults());
    }

    /**
     * Get movie videos (trailers, clips).
     *
     * @param tmdbId TMDB movie ID
     * @return List of videos
     */
    @Cacheable(value = "movieVideos", key = "#tmdbId")
    public List<VideoDto> getMovieVideos(Long tmdbId) {
        log.info("Fetching videos for movie: {}", tmdbId);

        TmdbVideosResponse response = tmdbClient.getMovieVideos(tmdbId, tmdbApiKey);

        return response.getResults().stream()
            .map(this::convertTmdbVideo)
            .toList();
    }

    /**
     * Get movie credits (cast and crew).
     *
     * @param tmdbId TMDB movie ID
     * @return Credits DTO
     */
    @Cacheable(value = "movieCredits", key = "#tmdbId")
    public CreditsDto getMovieCredits(Long tmdbId) {
        log.info("Fetching credits for movie: {}", tmdbId);

        TmdbCreditsResponse response = tmdbClient.getMovieCredits(tmdbId, tmdbApiKey);

        List<CastDto> cast = response.getCast().stream()
            .map(this::convertTmdbCast)
            .toList();

        List<CrewDto> crew = response.getCrew().stream()
            .map(this::convertTmdbCrew)
            .toList();

        return CreditsDto.builder()
            .movieId(tmdbId)
            .cast(cast)
            .crew(crew)
            .build();
    }

    /**
     * Get similar movies.
     *
     * @param tmdbId TMDB movie ID
     * @param page Page number
     * @param size Page size
     * @return Page of movies
     */
    @Cacheable(value = "movieLists", key = "'similar-' + #tmdbId + '-' + #page")
    public PageResponse<MovieListDto> getSimilarMovies(Long tmdbId, int page, int size) {
        log.info("Fetching similar movies for: {}, page={}", tmdbId, page);

        TmdbMovieListResponse response = tmdbClient.getSimilarMovies(tmdbId, tmdbApiKey, page);

        List<MovieListDto> movies = response.getResults().stream()
            .map(this::convertTmdbItemToListDto)
            .toList();

        return PageResponse.of(movies, page - 1, size, (long) response.getTotalResults());
    }

    /**
     * Get recommended movies.
     *
     * @param tmdbId TMDB movie ID
     * @param page Page number
     * @param size Page size
     * @return Page of movies
     */
    @Cacheable(value = "movieLists", key = "'recommendations-' + #tmdbId + '-' + #page")
    public PageResponse<MovieListDto> getRecommendedMovies(Long tmdbId, int page, int size) {
        log.info("Fetching recommendations for: {}, page={}", tmdbId, page);

        TmdbMovieListResponse response = tmdbClient.getRecommendedMovies(tmdbId, tmdbApiKey, page);

        List<MovieListDto> movies = response.getResults().stream()
            .map(this::convertTmdbItemToListDto)
            .toList();

        return PageResponse.of(movies, page - 1, size, (long) response.getTotalResults());
    }

    /**
     * Get all genres.
     *
     * @return List of genres
     */
    @Cacheable(value = "genres", key = "'all'")
    public List<GenreDto> getAllGenres() {
        log.info("Fetching all genres");

        TmdbGenresResponse response = tmdbClient.getGenres(tmdbApiKey);

        return response.getGenres().stream()
            .map(movieMapper::toDto)
            .toList();
    }

    // Helper methods

    private Movie convertAndSaveMovie(TmdbMovieResponse tmdbMovie) {
        Movie movie = Movie.builder()
            .tmdbId(tmdbMovie.getId())
            .title(tmdbMovie.getTitle())
            .overview(tmdbMovie.getOverview())
            .posterPath(tmdbMovie.getPosterPath())
            .backdropPath(tmdbMovie.getBackdropPath())
            .releaseDate(tmdbMovie.getReleaseDate())
            .voteAverage(tmdbMovie.getVoteAverage())
            .voteCount(tmdbMovie.getVoteCount())
            .genres(tmdbMovie.getGenres())
            .runtime(tmdbMovie.getRuntime())
            .status(tmdbMovie.getStatus())
            .budget(tmdbMovie.getBudget())
            .revenue(tmdbMovie.getRevenue())
            .spokenLanguages(tmdbMovie.getSpokenLanguages() != null ? 
                tmdbMovie.getSpokenLanguages().stream()
                    .map(TmdbMovieResponse.SpokenLanguage::getName)
                    .toList() : null)
            .productionCompanies(tmdbMovie.getProductionCompanies())
            .originalLanguage(tmdbMovie.getOriginalLanguage())
            .popularity(tmdbMovie.getPopularity())
            .adult(tmdbMovie.getAdult())
            .imdbId(tmdbMovie.getImdbId())
            .tagline(tmdbMovie.getTagline())
            .homepage(tmdbMovie.getHomepage())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .tmdbSyncVersion(1)
            .build();

        return movieRepository.save(Objects.requireNonNull(movie, "Movie cannot be null"));
    }

    private MovieListDto convertTmdbItemToListDto(TmdbMovieListResponse.TmdbMovieItem item) {
        return MovieListDto.builder()
            .tmdbId(item.getId())
            .title(item.getTitle())
            .overview(item.getOverview())
            .posterPath(item.getPosterPath())
            .backdropPath(item.getBackdropPath())
            .releaseDate(item.getReleaseDate() != null && !item.getReleaseDate().isEmpty() ? 
                LocalDate.parse(item.getReleaseDate()) : null)
            .voteAverage(item.getVoteAverage())
            .voteCount(item.getVoteCount())
            .popularity(item.getPopularity())
            .adult(item.getAdult())
            .build();
    }

    private VideoDto convertTmdbVideo(TmdbVideosResponse.TmdbVideo video) {
        return VideoDto.builder()
            .id(video.getId())
            .key(video.getKey())
            .name(video.getName())
            .site(video.getSite())
            .size(video.getSize())
            .type(video.getType())
            .official(video.getOfficial())
            .publishedAt(video.getPublishedAt())
            .build();
    }

    private CastDto convertTmdbCast(TmdbCreditsResponse.TmdbCast cast) {
        return CastDto.builder()
            .id(cast.getId())
            .name(cast.getName())
            .character(cast.getCharacter())
            .profilePath(cast.getProfilePath())
            .order(cast.getOrder())
            .build();
    }

    private CrewDto convertTmdbCrew(TmdbCreditsResponse.TmdbCrew crew) {
        return CrewDto.builder()
            .id(crew.getId())
            .name(crew.getName())
            .job(crew.getJob())
            .department(crew.getDepartment())
            .profilePath(crew.getProfilePath())
            .build();
    }
}

