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
import java.util.Set;

/**
 * Service layer for Movie operations.
 *
 * <p>Two consumers share this service (ADR-010): the native, camelCase
 * {@code /api/v1/movies} API and the TMDB-shaped facade
 * ({@link com.filmpire.movie.facade.TmdbFacadeController}) that exposes
 * TMDB's exact v3 paths/field names for the Filmpire React app. Both read
 * and write the SAME persisted {@link Movie} documents — there is one
 * dataset, not a cache-plus-source-of-truth split. Detail data is
 * near-immutable and served read-through from MongoDB once fetched; list
 * endpoints (discover/search/trending/popular/top-rated/similar/
 * recommendations) still ask TMDB live for ranking — its search/relevance
 * algorithm is not being reimplemented — but every movie any endpoint
 * touches is upserted, so the catalog grows from real traffic and repeat
 * detail lookups are served locally.</p>
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
     * Guards the fetch-from-TMDB path so concurrent misses for the same movie
     * don't trigger duplicate TMDB calls and duplicate MongoDB inserts.
     * ReentrantLock (not synchronized) to avoid pinning virtual threads.
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
        return movieMapper.toDto(getOrFetchMovieEntity(tmdbId));
    }

    /**
     * Core read-through/save-through lookup shared by the native API and the
     * facade: MongoDB first, TMDB on miss, save-through on fetch.
     *
     * @param tmdbId TMDB movie ID
     * @return the persisted (or freshly fetched-and-saved) movie entity
     */
    public Movie getOrFetchMovieEntity(Long tmdbId) {
        log.info("Fetching movie with TMDB ID: {}", tmdbId);

        return movieRepository.findByTmdbId(tmdbId)
            .map(movie -> {
                log.info("Movie found in MongoDB: {}", tmdbId);
                return movie;
            })
            .orElseGet(() -> {
                // Fetch from TMDB API with rate limiting/locking
                lock.lock();
                try {
                    // Double-check MongoDB inside lock
                    return movieRepository.findByTmdbId(tmdbId)
                        .orElseGet(() -> {
                            log.info("Movie not in MongoDB, fetching from TMDB: {}", tmdbId);
                            TmdbMovieResponse tmdbMovie = tmdbClient.getMovieDetails(tmdbId, tmdbApiKey);
                            return convertAndSaveMovie(tmdbMovie);
                        });
                } finally {
                    lock.unlock();
                }
            });
    }

    /**
     * Facade-facing detail lookup with TMDB's {@code append_to_response}
     * support: the movie entity, with {@code videos}/{@code credits}
     * populated (fetching and persisting them first if not already present)
     * when requested.
     *
     * @param tmdbId TMDB movie ID
     * @param appendToResponse requested sub-resources, e.g. {@code {"videos", "credits"}}
     * @return the movie entity, enriched as requested
     */
    public Movie getMovieForFacade(Long tmdbId, Set<String> appendToResponse) {
        Movie movie = getOrFetchMovieEntity(tmdbId);
        if (appendToResponse.contains("videos") && movie.getVideos() == null) {
            movie.setVideos(fetchAndSaveVideos(tmdbId));
        }
        if (appendToResponse.contains("credits") && movie.getCredits() == null) {
            movie.setCredits(fetchAndSaveCredits(tmdbId));
        }
        return movie;
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
        TmdbMovieListResponse response = discoverMoviesRaw(page, genreId, year, minRating, null);
        return toPageResponse(response, page, size);
    }

    /**
     * Facade-facing discover: same TMDB call as {@link #discoverMovies}, plus
     * {@code with_cast} (the React app's "movies by actor" query), returning
     * TMDB's own response shape directly. Every result is upserted.
     *
     * @param page      page number
     * @param genreId   {@code with_genres} filter
     * @param year      release-year filter
     * @param minRating minimum vote average filter
     * @param castId    {@code with_cast} filter (TMDB person id)
     * @return raw TMDB movie-list response
     */
    @Cacheable(value = "movieLists", key = "'discover-raw-' + #page + '-' + #genreId + '-' + #year + '-' + #minRating + '-' + #castId")
    public TmdbMovieListResponse discoverMoviesRaw(int page, Long genreId, Integer year, Double minRating, Long castId) {
        log.info("Discovering movies: page={}, genre={}, year={}, minRating={}, cast={}",
                 page, genreId, year, minRating, castId);
        TmdbMovieListResponse response = tmdbClient.discoverMovies(
            tmdbApiKey, page, "popularity.desc", genreId, year, minRating, castId
        );
        response.results().forEach(this::upsertFromListItem);
        return response;
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
        TmdbMovieListResponse response = searchMoviesRaw(query, page);
        return toPageResponse(response, page, size);
    }

    /**
     * Facade-facing search — same call, TMDB's own response shape.
     *
     * @param query search query
     * @param page  page number
     * @return raw TMDB movie-list response
     */
    @Cacheable(value = "movieLists", key = "'search-raw-' + #query + '-' + #page")
    public TmdbMovieListResponse searchMoviesRaw(String query, int page) {
        log.info("Searching movies: query={}, page={}", query, page);
        TmdbMovieListResponse response = tmdbClient.searchMovies(tmdbApiKey, query, page);
        response.results().forEach(this::upsertFromListItem);
        return response;
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
        response.results().forEach(this::upsertFromListItem);
        return toPageResponse(response, page, size);
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
        return toPageResponse(getMovieCategoryRaw("popular", page), page, size);
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
        return toPageResponse(getMovieCategoryRaw("top_rated", page), page, size);
    }

    /**
     * Facade-facing fixed-category list: TMDB's {@code popular},
     * {@code top_rated}, {@code upcoming} and {@code now_playing}. Every
     * result is upserted.
     *
     * @param category one of TMDB's fixed movie-list category names
     * @param page     page number
     * @return raw TMDB movie-list response
     */
    @Cacheable(value = "movieLists", key = "'category-raw-' + #category + '-' + #page")
    public TmdbMovieListResponse getMovieCategoryRaw(String category, int page) {
        log.info("Fetching '{}' movies: page={}", category, page);
        TmdbMovieListResponse response = switch (category) {
            case "popular" -> tmdbClient.getPopularMovies(tmdbApiKey, page);
            case "top_rated" -> tmdbClient.getTopRatedMovies(tmdbApiKey, page);
            case "upcoming" -> tmdbClient.getUpcomingMovies(tmdbApiKey, page);
            case "now_playing" -> tmdbClient.getNowPlayingMovies(tmdbApiKey, page);
            default -> throw new IllegalArgumentException("Unknown movie category: " + category);
        };
        response.results().forEach(this::upsertFromListItem);
        return response;
    }

    /**
     * Get movie videos (trailers, clips). Fetches and persists on miss so a
     * later {@code append_to_response=videos} detail request is served
     * locally.
     *
     * @param tmdbId TMDB movie ID
     * @return List of videos
     */
    @Cacheable(value = "movieVideos", key = "#tmdbId")
    public List<VideoDto> getMovieVideos(Long tmdbId) {
        return fetchAndSaveVideos(tmdbId).stream().map(movieMapper::toDto).toList();
    }

    /**
     * Get movie credits (cast and crew). Fetches and persists on miss so a
     * later {@code append_to_response=credits} detail request is served
     * locally.
     *
     * @param tmdbId TMDB movie ID
     * @return Credits DTO
     */
    @Cacheable(value = "movieCredits", key = "#tmdbId")
    public CreditsDto getMovieCredits(Long tmdbId) {
        return movieMapper.toDto(fetchAndSaveCredits(tmdbId));
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
        return toPageResponse(getSimilarMoviesRaw(tmdbId, page), page, size);
    }

    /**
     * Facade-facing similar-movies lookup — TMDB's own response shape.
     *
     * @param tmdbId TMDB movie ID
     * @param page   page number
     * @return raw TMDB movie-list response
     */
    @Cacheable(value = "movieLists", key = "'similar-raw-' + #tmdbId + '-' + #page")
    public TmdbMovieListResponse getSimilarMoviesRaw(Long tmdbId, int page) {
        log.info("Fetching similar movies for: {}, page={}", tmdbId, page);
        TmdbMovieListResponse response = tmdbClient.getSimilarMovies(tmdbId, tmdbApiKey, page);
        response.results().forEach(this::upsertFromListItem);
        return response;
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
        return toPageResponse(getRecommendedMoviesRaw(tmdbId, page), page, size);
    }

    /**
     * Facade-facing recommendations lookup — TMDB's own response shape.
     *
     * @param tmdbId TMDB movie ID
     * @param page   page number
     * @return raw TMDB movie-list response
     */
    @Cacheable(value = "movieLists", key = "'recommendations-raw-' + #tmdbId + '-' + #page")
    public TmdbMovieListResponse getRecommendedMoviesRaw(Long tmdbId, int page) {
        log.info("Fetching recommendations for: {}, page={}", tmdbId, page);
        TmdbMovieListResponse response = tmdbClient.getRecommendedMovies(tmdbId, tmdbApiKey, page);
        response.results().forEach(this::upsertFromListItem);
        return response;
    }

    /**
     * Get all genres. TMDB's genre catalog is small (~19 entries) and
     * effectively static, so unlike movies it is not upserted into its own
     * collection — Redis caching (below) and TMDB's own stability are enough.
     *
     * @return List of genres
     */
    @Cacheable(value = "genres", key = "'all'")
    public List<GenreDto> getAllGenres() {
        return getGenresRaw().genres().stream().map(movieMapper::toDto).toList();
    }

    /**
     * Facade-facing genre list — TMDB's own response shape.
     *
     * @return raw TMDB genre list response
     */
    @Cacheable(value = "genres", key = "'raw'")
    public TmdbGenresResponse getGenresRaw() {
        log.info("Fetching all genres");
        return tmdbClient.getGenres(tmdbApiKey);
    }

    // Helper methods

    private List<Video> fetchAndSaveVideos(Long tmdbId) {
        log.info("Fetching videos for movie: {}", tmdbId);
        TmdbVideosResponse response = tmdbClient.getMovieVideos(tmdbId, tmdbApiKey);
        List<Video> videos = response.results().stream().map(this::convertTmdbVideo).toList();

        movieRepository.findByTmdbId(tmdbId).ifPresent(movie -> {
            movie.setVideos(videos);
            movie.setUpdatedAt(LocalDateTime.now());
            movieRepository.save(movie);
        });
        return videos;
    }

    private Credits fetchAndSaveCredits(Long tmdbId) {
        log.info("Fetching credits for movie: {}", tmdbId);
        TmdbCreditsResponse response = tmdbClient.getMovieCredits(tmdbId, tmdbApiKey);
        Credits credits = Credits.builder()
            .movieId(tmdbId)
            .cast(response.cast().stream().map(this::convertTmdbCast).toList())
            .crew(response.crew().stream().map(this::convertTmdbCrew).toList())
            .build();

        movieRepository.findByTmdbId(tmdbId).ifPresent(movie -> {
            movie.setCredits(credits);
            movie.setUpdatedAt(LocalDateTime.now());
            movieRepository.save(movie);
        });
        return credits;
    }

    /**
     * Upserts a list-endpoint result into MongoDB. List responses only carry
     * a subset of a movie's fields (no runtime/budget/credits/etc.), so an
     * existing, more-detailed record is updated in place rather than
     * clobbered — detail-only fields are left untouched. Genres are
     * deliberately NOT set here: list items carry only {@code genre_ids}
     * (no names), and overwriting a movie's typed {@code genres} with
     * name-less stubs would regress the native API's already-correct output
     * for any movie previously seen via a detail fetch.
     *
     * @param item a single result from any TMDB list endpoint
     * @return the upserted movie
     */
    private Movie upsertFromListItem(TmdbMovieListResponse.TmdbMovieItem item) {
        Movie movie = movieRepository.findByTmdbId(item.id()).orElseGet(() -> {
            Movie fresh = new Movie();
            fresh.setTmdbId(item.id());
            fresh.setCreatedAt(LocalDateTime.now());
            fresh.setTmdbSyncVersion(1);
            return fresh;
        });

        movie.setTitle(item.title());
        movie.setOverview(item.overview());
        movie.setPosterPath(item.posterPath());
        movie.setBackdropPath(item.backdropPath());
        movie.setReleaseDate(parseReleaseDate(item.releaseDate()));
        movie.setVoteAverage(item.voteAverage());
        movie.setVoteCount(item.voteCount());
        movie.setPopularity(item.popularity());
        movie.setAdult(item.adult());
        movie.setOriginalLanguage(item.originalLanguage());
        movie.setUpdatedAt(LocalDateTime.now());

        return movieRepository.save(movie);
    }

    private static LocalDate parseReleaseDate(String releaseDate) {
        return releaseDate != null && !releaseDate.isEmpty() ? LocalDate.parse(releaseDate) : null;
    }

    private PageResponse<MovieListDto> toPageResponse(TmdbMovieListResponse response, int page, int size) {
        List<MovieListDto> movies = response.results().stream().map(this::convertTmdbItemToListDto).toList();
        return PageResponse.of(movies, page - 1, size, (long) response.totalResults());
    }

    private Movie convertAndSaveMovie(TmdbMovieResponse tmdbMovie) {
        Movie movie = Movie.builder()
            .tmdbId(tmdbMovie.id())
            .title(tmdbMovie.title())
            .originalTitle(tmdbMovie.originalTitle())
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
            .spokenLanguages(tmdbMovie.spokenLanguages())
            .productionCompanies(tmdbMovie.productionCompanies())
            .productionCountries(tmdbMovie.productionCountries())
            .belongsToCollection(tmdbMovie.belongsToCollection())
            .video(tmdbMovie.video())
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
            .releaseDate(parseReleaseDate(item.releaseDate()))
            .voteAverage(item.voteAverage())
            .voteCount(item.voteCount())
            .popularity(item.popularity())
            .adult(item.adult())
            .build();
    }

    private Video convertTmdbVideo(TmdbVideosResponse.TmdbVideo video) {
        return Video.builder()
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

    private Cast convertTmdbCast(TmdbCreditsResponse.TmdbCast cast) {
        return Cast.builder()
            .id(cast.id())
            .name(cast.name())
            .character(cast.character())
            .profilePath(cast.profilePath())
            .order(cast.order())
            .build();
    }

    private Crew convertTmdbCrew(TmdbCreditsResponse.TmdbCrew crew) {
        return Crew.builder()
            .id(crew.id())
            .name(crew.name())
            .job(crew.job())
            .department(crew.department())
            .profilePath(crew.profilePath())
            .build();
    }
}
