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
    private final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();

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
                // Fetch from TMDB API with rate limiting/locking
                lock.lock();
                try {
                    // Double-check MongoDB inside lock
                    return movieRepository.findByTmdbId(tmdbId)
                        .map(movieMapper::toDto)
                        .orElseGet(() -> {
                            log.info("Movie not in MongoDB, fetching from TMDB: {}", tmdbId);
                            TmdbMovieResponse tmdbMovie = tmdbClient.getMovieDetails(tmdbId, tmdbApiKey);
                            Movie movie = convertAndSaveMovie(tmdbMovie);
                            return movieMapper.toDto(movie);
                        });
                } finally {
                    lock.unlock();
                }
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

        List<MovieListDto> movies = response.results().stream()
            .map(this::convertTmdbItemToListDto)
            .toList();

        return PageResponse.of(movies, page - 1, size, (long) response.totalResults());
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

        List<MovieListDto> movies = response.results().stream()
            .map(this::convertTmdbItemToListDto)
            .toList();

        return PageResponse.of(movies, page - 1, size, (long) response.totalResults());
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

        List<MovieListDto> movies = response.results().stream()
            .map(this::convertTmdbItemToListDto)
            .toList();

        return PageResponse.of(movies, page - 1, size, (long) response.totalResults());
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

        List<MovieListDto> movies = response.results().stream()
            .map(this::convertTmdbItemToListDto)
            .toList();

        return PageResponse.of(movies, page - 1, size, (long) response.totalResults());
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

        List<MovieListDto> movies = response.results().stream()
            .map(this::convertTmdbItemToListDto)
            .toList();

        return PageResponse.of(movies, page - 1, size, (long) response.totalResults());
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

        return response.results().stream()
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

        List<CastDto> cast = response.cast().stream()
            .map(this::convertTmdbCast)
            .toList();

        List<CrewDto> crew = response.crew().stream()
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

        List<MovieListDto> movies = response.results().stream()
            .map(this::convertTmdbItemToListDto)
            .toList();

        return PageResponse.of(movies, page - 1, size, (long) response.totalResults());
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

        List<MovieListDto> movies = response.results().stream()
            .map(this::convertTmdbItemToListDto)
            .toList();

        return PageResponse.of(movies, page - 1, size, (long) response.totalResults());
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

        return response.genres().stream()
            .map(movieMapper::toDto)
            .toList();
    }

    // Helper methods

    private Movie convertAndSaveMovie(TmdbMovieResponse tmdbMovie) {
        Movie movie = Movie.builder()
            .tmdbId(tmdbMovie.id())
            .title(tmdbMovie.title())
            .overview(tmdbMovie.overview())
            .posterPath(tmdbMovie.posterPath())
            .backdropPath(tmdbMovie.backdropPath())
            .releaseDate(tmdbMovie.releaseDate())
            .voteAverage(tmdbMovie.voteAverage())
            .voteCount(tmdbMovie.voteCount())
            .genres(tmdbMovie.genres())
            .runtime(tmdbMovie.runtime())
            .status(tmdbMovie.status())
            .budget(tmdbMovie.budget())
            .revenue(tmdbMovie.revenue())
            .spokenLanguages(tmdbMovie.spokenLanguages() != null ? 
                tmdbMovie.spokenLanguages().stream()
                    .map(TmdbMovieResponse.SpokenLanguage::name)
                    .toList() : null)
            .productionCompanies(tmdbMovie.productionCompanies())
            .originalLanguage(tmdbMovie.originalLanguage())
            .popularity(tmdbMovie.popularity())
            .adult(tmdbMovie.adult())
            .imdbId(tmdbMovie.imdbId())
            .tagline(tmdbMovie.tagline())
            .homepage(tmdbMovie.homepage())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .tmdbSyncVersion(1)
            .build();

        return movieRepository.save(Objects.requireNonNull(movie, "Movie cannot be null"));
    }

    private MovieListDto convertTmdbItemToListDto(TmdbMovieListResponse.TmdbMovieItem item) {
        return MovieListDto.builder()
            .tmdbId(item.id())
            .title(item.title())
            .overview(item.overview())
            .posterPath(item.posterPath())
            .backdropPath(item.backdropPath())
            .releaseDate(item.releaseDate() != null && !item.releaseDate().isEmpty() ? 
                LocalDate.parse(item.releaseDate()) : null)
            .voteAverage(item.voteAverage())
            .voteCount(item.voteCount())
            .popularity(item.popularity())
            .adult(item.adult())
            .build();
    }

    private VideoDto convertTmdbVideo(TmdbVideosResponse.TmdbVideo video) {
        return VideoDto.builder()
            .id(video.id())
            .key(video.key())
            .name(video.name())
            .site(video.site())
            .size(video.size())
            .type(video.type())
            .official(video.official())
            .publishedAt(video.publishedAt())
            .build();
    }

    private CastDto convertTmdbCast(TmdbCreditsResponse.TmdbCast cast) {
        return CastDto.builder()
            .id(cast.id())
            .name(cast.name())
            .character(cast.character())
            .profilePath(cast.profilePath())
            .order(cast.order())
            .build();
    }

    private CrewDto convertTmdbCrew(TmdbCreditsResponse.TmdbCrew crew) {
        return CrewDto.builder()
            .id(crew.id())
            .name(crew.name())
            .job(crew.job())
            .department(crew.department())
            .profilePath(crew.profilePath())
            .build();
    }
}

