package dev.lmdb.movie.service;

import dev.lmdb.movie.client.TmdbClient;
import dev.lmdb.movie.client.dto.*;
import dev.lmdb.movie.dto.*;
import dev.lmdb.movie.mapper.MovieMapper;
import dev.lmdb.movie.model.*;
import dev.lmdb.movie.repository.MovieRepository;
import dev.lmdb.shared.dto.PageResponse;
import dev.lmdb.shared.exception.ValidationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.convert.ConversionException;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Service layer for Movie operations.
 *
 * <p>Two consumers share this service (ADR-010): the native, camelCase {@code /api/v1/movies} API
 * and the TMDB-shaped facade ({@link dev.lmdb.movie.facade.TmdbFacadeController}) that exposes
 * TMDB's exact v3 paths/field names for the LMDB React app. Both read and write the SAME persisted
 * {@link Movie} documents — there is one dataset, not a cache-plus-source-of-truth split. Detail
 * data is near-immutable and served read-through from MongoDB once fetched; list endpoints
 * (discover/search/trending/popular/top-rated/similar/ recommendations) still ask TMDB live for
 * ranking — its search/relevance algorithm is not being reimplemented — but every movie any
 * endpoint touches is upserted, so the catalog grows from real traffic and repeat detail lookups
 * are served locally.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MovieService {

  private final MovieRepository movieRepository;
  private final TmdbClient tmdbClient;
  private final MovieMapper movieMapper;

  /**
   * Used only to delete schema-drifted documents by query — see {@link #findPersistedMovie(Long)}.
   * A derived {@code deleteByTmdbId} cannot do this job: Spring Data loads the entity before
   * deleting it, which rethrows the very mapping error we are recovering from.
   */
  private final MongoTemplate mongoTemplate;

  /**
   * Self-reference used to invoke this bean's own {@code @Cacheable} methods through the Spring
   * proxy (issue #20 / rule java:S6809).
   *
   * <p>The {@code *Raw} methods are cached AND called from two directions: externally by {@link
   * dev.lmdb.movie.facade.TmdbFacadeController} (which goes through the proxy, so the cache
   * applies) and internally by the native {@code /api/v1} methods below. A plain {@code
   * this.getMovieCategoryRaw(...)} call bypasses the proxy entirely, so the internal path silently
   * skipped the shared cache and re-fetched from TMDB even when the facade had just cached the
   * identical response. Routing through the proxy lets both API layers share one cached upstream
   * call — which matters, because TMDB rate-limits.
   *
   * <p>{@link ObjectProvider} rather than {@code @Lazy} self-injection: it resolves on demand, so
   * there is no circular-dependency dance at construction time and it composes with Lombok's
   * generated constructor.
   */
  private final ObjectProvider<MovieService> selfProvider;

  @Value("${tmdb.api.key}")
  private String tmdbApiKey;

  /**
   * This bean as seen through its Spring proxy, so {@code @Cacheable} applies.
   *
   * @return the proxied instance of this service
   */
  private MovieService self() {
    return selfProvider.getObject();
  }

  /**
   * Guards the fetch-from-TMDB path so concurrent misses for the same movie don't trigger duplicate
   * TMDB calls and duplicate MongoDB inserts. ReentrantLock (not synchronized) to avoid pinning
   * virtual threads.
   */
  private final java.util.concurrent.locks.ReentrantLock lock =
      new java.util.concurrent.locks.ReentrantLock();

  /**
   * Get movie by ID with hybrid caching. 1. Check Redis cache (via @Cacheable) 2. Check MongoDB 3.
   * Fetch from TMDB API and store in MongoDB + Redis
   *
   * @param tmdbId TMDB movie ID
   * @return Movie DTO
   */
  @Cacheable(value = "movies", key = "#tmdbId", sync = true)
  public MovieDto getMovieById(Long tmdbId) {
    return movieMapper.toDto(getOrFetchMovieEntity(tmdbId));
  }

  /**
   * Core read-through/save-through lookup shared by the native API and the facade: MongoDB first,
   * TMDB on miss, save-through on fetch.
   *
   * @param tmdbId TMDB movie ID
   * @return the persisted (or freshly fetched-and-saved) movie entity
   */
  public Movie getOrFetchMovieEntity(Long tmdbId) {
    log.info("Fetching movie with TMDB ID: {}", tmdbId);

    return findPersistedMovie(tmdbId)
        .map(
            movie -> {
              log.info("Movie found in MongoDB: {}", tmdbId);
              return completeIfListItemOnly(movie);
            })
        .orElseGet(
            () -> {
              // Fetch from TMDB API with rate limiting/locking
              lock.lock();
              try {
                // Double-check MongoDB inside lock
                return findPersistedMovie(tmdbId)
                    .map(this::completeIfListItemOnly)
                    .orElseGet(
                        () -> {
                          log.info("Movie not in MongoDB, fetching from TMDB: {}", tmdbId);
                          TmdbMovieResponse tmdbMovie =
                              tmdbClient.getMovieDetails(tmdbId, tmdbApiKey);
                          return convertAndSaveMovie(tmdbMovie);
                        });
              } finally {
                lock.unlock();
              }
            });
  }

  /**
   * Ensures a persisted movie has its detail-only fields populated, fetching and merging them from
   * TMDB if not. {@code runtime} is the completeness signal — it is set only by a detail fetch,
   * never by a list upsert.
   *
   * @param movie a persisted movie, possibly list-item-only
   * @return {@code movie} unchanged if already detail-complete, otherwise the same document with
   *     detail fields merged in and re-saved
   */
  private Movie completeIfListItemOnly(Movie movie) {
    if (movie.getRuntime() != null) {
      return movie;
    }
    log.info(
        "Movie {} was only ever seen via a list — fetching full detail from TMDB",
        movie.getTmdbId());
    TmdbMovieResponse tmdbMovie = tmdbClient.getMovieDetails(movie.getTmdbId(), tmdbApiKey);
    applyDetailFields(movie, tmdbMovie);
    movie.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
    return movieRepository.save(movie);
  }

  /**
   * Reads a persisted movie, treating a document that no longer maps to the current model as a MISS
   * rather than an error (issue #46).
   *
   * <p>MongoDB is schemaless and, unlike the JPA services, movie-service has no Flyway/{@code
   * ddl-auto: validate} gate that would catch schema drift at startup. So a document written by an
   * older model version — e.g. {@code spokenLanguages} persisted as {@code List<String>} before
   * ADR-010 changed it to {@code List<SpokenLanguage>} — sits unnoticed until something reads it,
   * and then throws on every single request, forever, because nothing ever overwrites it.
   *
   * <p>Under ADR-010 the catalog is seeded from TMDB and re-derivable, so an unreadable document is
   * semantically just a cache miss. Discarding it and falling through to the normal fetch +
   * save-through path rewrites it in the current shape: the first request after a model change
   * costs one TMDB call and self-heals, and no operator has to go delete documents by hand.
   *
   * <p>Only mapping/conversion failures are swallowed. Genuine infrastructure faults (Mongo
   * unreachable, auth failure) are {@code DataAccessException}s and propagate untouched — masking
   * those as a miss would turn an outage into a silent stampede of TMDB traffic.
   *
   * @param tmdbId TMDB movie ID
   * @return the persisted movie, or empty if absent OR undeserializable
   */
  private Optional<Movie> findPersistedMovie(Long tmdbId) {
    try {
      return movieRepository.findByTmdbId(tmdbId);
    } catch (ConversionException | MappingException e) {
      // Schema drift, not an outage: the document exists but was written
      // against an older model. Drop it and let the caller re-fetch.
      log.warn(
          "Movie {} is persisted in a shape the current model cannot read ({}: {}). "
              + "Discarding it and re-fetching from TMDB.",
          tmdbId,
          e.getClass().getSimpleName(),
          e.getMessage());
      evictUnreadableMovie(tmdbId);
      return Optional.empty();
    }
  }

  /**
   * Deletes a movie document by query, without ever converting it to an entity — the whole point,
   * since converting is what fails.
   *
   * <p>Best-effort: if the delete itself fails the caller still proceeds to fetch fresh data from
   * TMDB, so the request succeeds either way. The stale document would simply be retried (and
   * re-reported) next time.
   *
   * <p>Matches on {@code tmdbId} alone, deliberately. {@code Criteria.byExample} looks equivalent
   * but is not: it derives a typed example, which adds a {@code _class} restriction to the query. A
   * drifted document need not carry a matching {@code _class} — one written by the raw driver has
   * none at all — so the delete silently removes nothing, the re-fetch inserts a second document
   * alongside the broken one, and the heal never sticks. This must stay a plain field match.
   *
   * @param tmdbId TMDB movie ID of the document to discard
   */
  private void evictUnreadableMovie(Long tmdbId) {
    try {
      long removed =
          mongoTemplate
              .remove(Query.query(Criteria.where(Movie.Fields.tmdbId).is(tmdbId)), Movie.class)
              .getDeletedCount();
      log.info("Evicted {} unreadable document(s) for movie {}", removed, tmdbId);
    } catch (RuntimeException e) {
      log.error(
          "Could not evict unreadable document for movie {} — serving a fresh "
              + "fetch anyway, but the stale document is still there",
          tmdbId,
          e);
    }
  }

  /**
   * Facade-facing detail lookup with TMDB's {@code append_to_response} support: the movie entity,
   * with {@code videos}/{@code credits} populated (fetching and persisting them first if not
   * already present) when requested.
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
   * Discover movies with filters, including an optional release-year range (#218, ADR-020)
   * alongside the pre-existing exact-year filter.
   *
   * @param page Page number
   * @param size Page size
   * @param genreId Genre ID filter
   * @param year Release year filter (exact match) — mutually exclusive with {@code yearFrom}/{@code
   *     yearTo}
   * @param yearFrom Release-year range start, inclusive; mutually exclusive with {@code year}
   * @param yearTo Release-year range end, inclusive; mutually exclusive with {@code year}
   * @param minRating Minimum rating filter
   * @return Page of movies
   * @throws ValidationException if both {@code year} and a range bound are given — an ambiguous
   *     request rejected explicitly rather than silently picking one filter over the other
   */
  @Cacheable(
      value = "movieLists",
      key =
          "'discover-' + #page + '-' + #size + '-' + #genreId + '-' + #year + '-' + #yearFrom + '-'"
              + " + #yearTo + '-' + #minRating",
      sync = true)
  public PageResponse<MovieListDto> discoverMovies(
      int page,
      int size,
      Long genreId,
      Integer year,
      Integer yearFrom,
      Integer yearTo,
      Double minRating) {
    rejectConflictingYearFilters(year, yearFrom, yearTo);
    TmdbMovieListResponse response =
        self().discoverMoviesRaw(page, genreId, year, yearFrom, yearTo, minRating, null);
    return toPageResponse(response, page, size);
  }

  /**
   * Rejects a request that names both the exact-year filter and a range bound, rather than letting
   * one silently win over the other — #218 AC4.
   *
   * @param year the exact-year filter, or {@code null}
   * @param yearFrom the range's start, or {@code null}
   * @param yearTo the range's end, or {@code null}
   * @throws ValidationException if {@code year} and either range bound are both non-null
   */
  private static void rejectConflictingYearFilters(Integer year, Integer yearFrom, Integer yearTo) {
    if (year != null && (yearFrom != null || yearTo != null)) {
      throw new ValidationException(
          "year", "year cannot be combined with yearFrom/yearTo — use one filter or the other");
    }
  }

  /**
   * Facade-facing discover: same TMDB call as {@link #discoverMovies}, plus {@code with_cast} (the
   * React app's "movies by actor" query), returning TMDB's own response shape directly. Every
   * result is upserted.
   *
   * <p>The year-range filter (#218) is reachable through this raw method — {@link #discoverMovies}
   * validates then delegates its own {@code yearFrom}/{@code yearTo} here — but {@link
   * dev.lmdb.movie.facade.TmdbFacadeController#discover} itself does not accept or pass them: the
   * facade's own scope stays exact-year-only, per #218's Scope note.
   *
   * @param page page number
   * @param genreId {@code with_genres} filter
   * @param year release-year filter (exact match)
   * @param yearFrom release-year range start, inclusive
   * @param yearTo release-year range end, inclusive
   * @param minRating minimum vote average filter
   * @param castId {@code with_cast} filter (TMDB person id)
   * @return raw TMDB movie-list response
   */
  @Cacheable(
      value = "movieLists",
      key =
          "'discover-raw-' + #page + '-' + #genreId + '-' + #year + '-' + #yearFrom + '-' + #yearTo"
              + " + '-' + #minRating + '-' + #castId",
      sync = true)
  public TmdbMovieListResponse discoverMoviesRaw(
      int page,
      Long genreId,
      Integer year,
      Integer yearFrom,
      Integer yearTo,
      Double minRating,
      Long castId) {
    log.info(
        "Discovering movies: page={}, genre={}, year={}, yearFrom={}, yearTo={}, minRating={},"
            + " cast={}",
        page,
        genreId,
        year,
        yearFrom,
        yearTo,
        minRating,
        castId);
    TmdbMovieListResponse response =
        tmdbClient.discoverMovies(
            tmdbApiKey,
            page,
            "popularity.desc",
            genreId,
            year,
            toTmdbDateBound(yearFrom, 1, 1),
            toTmdbDateBound(yearTo, 12, 31),
            minRating,
            castId);
    response.results().forEach(this::upsertFromListItem);
    return response;
  }

  /**
   * Converts a bare year into the {@code YYYY-MM-DD} form TMDB's {@code
   * primary_release_date.gte}/{@code primary_release_date.lte} params require — a year alone isn't
   * a valid value for either param.
   *
   * @param year the year, or {@code null} if this bound wasn't given
   * @param month the month to anchor the bound to (1 for a range start, 12 for a range end)
   * @param day the day to anchor the bound to (1 for a range start, 31 for a range end)
   * @return {@code "{year}-{month}-{day}"}, zero-padded, or {@code null} if {@code year} is {@code
   *     null}
   */
  private static String toTmdbDateBound(Integer year, int month, int day) {
    return year == null ? null : "%d-%02d-%02d".formatted(year, month, day);
  }

  /**
   * Search movies by query.
   *
   * @param query Search query
   * @param page Page number
   * @param size Page size
   * @return Page of movies
   */
  @Cacheable(value = "movieLists", key = "'search-' + #query + '-' + #page", sync = true)
  public PageResponse<MovieListDto> searchMovies(String query, int page, int size) {
    TmdbMovieListResponse response = self().searchMoviesRaw(query, page);
    return toPageResponse(response, page, size);
  }

  /**
   * Facade-facing search — same call, TMDB's own response shape.
   *
   * @param query search query
   * @param page page number
   * @return raw TMDB movie-list response
   */
  @Cacheable(value = "movieLists", key = "'search-raw-' + #query + '-' + #page", sync = true)
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
  @Cacheable(value = "movieLists", key = "'trending-' + #timeWindow + '-' + #page", sync = true)
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
  @Cacheable(value = "movieLists", key = "'popular-' + #page", sync = true)
  public PageResponse<MovieListDto> getPopularMovies(int page, int size) {
    return toPageResponse(self().getMovieCategoryRaw("popular", page), page, size);
  }

  /**
   * Get top-rated movies.
   *
   * @param page Page number
   * @param size Page size
   * @return Page of movies
   */
  @Cacheable(value = "movieLists", key = "'toprated-' + #page", sync = true)
  public PageResponse<MovieListDto> getTopRatedMovies(int page, int size) {
    return toPageResponse(self().getMovieCategoryRaw("top_rated", page), page, size);
  }

  /**
   * Facade-facing fixed-category list: TMDB's {@code popular}, {@code top_rated}, {@code upcoming}
   * and {@code now_playing}. Every result is upserted.
   *
   * @param category one of TMDB's fixed movie-list category names
   * @param page page number
   * @return raw TMDB movie-list response
   */
  @Cacheable(value = "movieLists", key = "'category-raw-' + #category + '-' + #page", sync = true)
  public TmdbMovieListResponse getMovieCategoryRaw(String category, int page) {
    log.info("Fetching '{}' movies: page={}", category, page);
    TmdbMovieListResponse response =
        switch (category) {
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
   * Get movie videos (trailers, clips). Fetches and persists on miss so a later {@code
   * append_to_response=videos} detail request is served locally.
   *
   * @param tmdbId TMDB movie ID
   * @return List of videos
   */
  @Cacheable(value = "movieVideos", key = "#tmdbId", sync = true)
  public List<VideoDto> getMovieVideos(Long tmdbId) {
    return fetchAndSaveVideos(tmdbId).stream().map(movieMapper::toDto).toList();
  }

  /**
   * Get movie credits (cast and crew). Fetches and persists on miss so a later {@code
   * append_to_response=credits} detail request is served locally.
   *
   * @param tmdbId TMDB movie ID
   * @return Credits DTO
   */
  @Cacheable(value = "movieCredits", key = "#tmdbId", sync = true)
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
  @Cacheable(value = "movieLists", key = "'similar-' + #tmdbId + '-' + #page", sync = true)
  public PageResponse<MovieListDto> getSimilarMovies(Long tmdbId, int page, int size) {
    return toPageResponse(self().getSimilarMoviesRaw(tmdbId, page), page, size);
  }

  /**
   * Facade-facing similar-movies lookup — TMDB's own response shape.
   *
   * @param tmdbId TMDB movie ID
   * @param page page number
   * @return raw TMDB movie-list response
   */
  @Cacheable(value = "movieLists", key = "'similar-raw-' + #tmdbId + '-' + #page", sync = true)
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
  @Cacheable(value = "movieLists", key = "'recommendations-' + #tmdbId + '-' + #page", sync = true)
  public PageResponse<MovieListDto> getRecommendedMovies(Long tmdbId, int page, int size) {
    return toPageResponse(self().getRecommendedMoviesRaw(tmdbId, page), page, size);
  }

  /**
   * Facade-facing recommendations lookup — TMDB's own response shape.
   *
   * @param tmdbId TMDB movie ID
   * @param page page number
   * @return raw TMDB movie-list response
   */
  @Cacheable(
      value = "movieLists",
      key = "'recommendations-raw-' + #tmdbId + '-' + #page",
      sync = true)
  public TmdbMovieListResponse getRecommendedMoviesRaw(Long tmdbId, int page) {
    log.info("Fetching recommendations for: {}, page={}", tmdbId, page);
    TmdbMovieListResponse response = tmdbClient.getRecommendedMovies(tmdbId, tmdbApiKey, page);
    response.results().forEach(this::upsertFromListItem);
    return response;
  }

  /**
   * Get all genres. TMDB's genre catalog is small (~19 entries) and effectively static, so unlike
   * movies it is not upserted into its own collection — Redis caching (below) and TMDB's own
   * stability are enough.
   *
   * @return List of genres
   */
  @Cacheable(value = "genres", key = "'all'", sync = true)
  public List<GenreDto> getAllGenres() {
    return self().getGenresRaw().genres().stream().map(movieMapper::toDto).toList();
  }

  /**
   * Facade-facing genre list — TMDB's own response shape.
   *
   * @return raw TMDB genre list response
   */
  @Cacheable(value = "genres", key = "'raw'", sync = true)
  public TmdbGenresResponse getGenresRaw() {
    log.info("Fetching all genres");
    return tmdbClient.getGenres(tmdbApiKey);
  }

  /**
   * Catalog retention policy: removes list-sourced movie stubs (documents where {@code runtime} is
   * null, meaning detail fields were never fetched) that have not been updated within the specified
   * retention period. Full detail documents (where {@code runtime} is non-null) are preserved
   * indefinitely.
   *
   * @param maxAgeDays maximum age in days for list-sourced stubs before cleanup
   * @return count of evicted stub documents
   */
  public long cleanupListSourcedStubs(int maxAgeDays) {
    LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(maxAgeDays);
    Query query =
        Query.query(
            Criteria.where(Movie.Fields.runtime).isNull().and(Movie.Fields.updatedAt).lt(cutoff));
    long removed = mongoTemplate.remove(query, Movie.class).getDeletedCount();
    log.info(
        "Catalog retention cleanup: evicted {} list-sourced movie stubs updated before {}",
        removed,
        cutoff);
    return removed;
  }

  // Helper methods

  private List<Video> fetchAndSaveVideos(Long tmdbId) {
    log.info("Fetching videos for movie: {}", tmdbId);
    TmdbVideosResponse response = tmdbClient.getMovieVideos(tmdbId, tmdbApiKey);
    List<Video> videos = response.results().stream().map(this::convertTmdbVideo).toList();

    findPersistedMovie(tmdbId)
        .ifPresent(
            movie -> {
              movie.setVideos(videos);
              movie.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
              movieRepository.save(movie);
            });
    return videos;
  }

  private Credits fetchAndSaveCredits(Long tmdbId) {
    log.info("Fetching credits for movie: {}", tmdbId);
    TmdbCreditsResponse response = tmdbClient.getMovieCredits(tmdbId, tmdbApiKey);
    Credits credits =
        Credits.builder()
            .movieId(tmdbId)
            .cast(response.cast().stream().map(this::convertTmdbCast).toList())
            .crew(response.crew().stream().map(this::convertTmdbCrew).toList())
            .build();

    findPersistedMovie(tmdbId)
        .ifPresent(
            movie -> {
              movie.setCredits(credits);
              movie.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
              movieRepository.save(movie);
            });
    return credits;
  }

  /**
   * Upserts a list-endpoint result into MongoDB. List responses only carry a subset of a movie's
   * fields (no runtime/budget/credits/etc.), so an existing, more-detailed record is updated in
   * place rather than clobbered — detail-only fields are left untouched. Genres are deliberately
   * NOT set here: list items carry only {@code genre_ids} (no names), and overwriting a movie's
   * typed {@code genres} with name-less stubs would regress the native API's already-correct output
   * for any movie previously seen via a detail fetch.
   *
   * @param item a single result from any TMDB list endpoint
   * @return the upserted movie
   */
  private Movie upsertFromListItem(TmdbMovieListResponse.TmdbMovieItem item) {
    Movie movie =
        findPersistedMovie(item.id())
            .orElseGet(
                () -> {
                  Movie fresh = new Movie();
                  fresh.setTmdbId(item.id());
                  fresh.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
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
    movie.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

    return movieRepository.save(movie);
  }

  private static LocalDate parseReleaseDate(String releaseDate) {
    return releaseDate != null && !releaseDate.isEmpty() ? LocalDate.parse(releaseDate) : null;
  }

  private PageResponse<MovieListDto> toPageResponse(
      TmdbMovieListResponse response, int page, int size) {
    List<MovieListDto> movies =
        response.results().stream().map(this::convertTmdbItemToListDto).toList();
    return PageResponse.of(movies, page - 1, size, response.totalResults());
  }

  private Movie convertAndSaveMovie(TmdbMovieResponse tmdbMovie) {
    Movie movie = new Movie();
    movie.setTmdbId(tmdbMovie.id());
    movie.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
    movie.setTmdbSyncVersion(1);
    applyDetailFields(movie, tmdbMovie);
    movie.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

    return movieRepository.save(Objects.requireNonNull(movie, "Movie cannot be null"));
  }

  /**
   * Copies TMDB detail-response fields onto a Movie entity. Used by both {@link
   * #convertAndSaveMovie} and {@link #completeIfListItemOnly}. Does not touch {@code id}, {@code
   * createdAt}, or {@code tmdbSyncVersion}.
   *
   * @param movie the entity to populate, mutated in place
   * @param tmdbMovie the TMDB detail response to copy fields from
   */
  private void applyDetailFields(Movie movie, TmdbMovieResponse tmdbMovie) {
    movie.setTitle(tmdbMovie.title());
    movie.setOriginalTitle(tmdbMovie.originalTitle());
    movie.setOverview(tmdbMovie.overview());
    movie.setPosterPath(tmdbMovie.posterPath());
    movie.setBackdropPath(tmdbMovie.backdropPath());
    movie.setReleaseDate(tmdbMovie.releaseDate());
    movie.setVoteAverage(tmdbMovie.voteAverage());
    movie.setVoteCount(tmdbMovie.voteCount());
    movie.setGenres(tmdbMovie.genres());
    movie.setRuntime(tmdbMovie.runtime());
    movie.setStatus(tmdbMovie.status());
    movie.setBudget(tmdbMovie.budget());
    movie.setRevenue(tmdbMovie.revenue());
    movie.setSpokenLanguages(tmdbMovie.spokenLanguages());
    movie.setProductionCompanies(tmdbMovie.productionCompanies());
    movie.setProductionCountries(tmdbMovie.productionCountries());
    movie.setBelongsToCollection(tmdbMovie.belongsToCollection());
    movie.setVideo(tmdbMovie.video());
    movie.setOriginalLanguage(tmdbMovie.originalLanguage());
    movie.setPopularity(tmdbMovie.popularity());
    movie.setAdult(tmdbMovie.adult());
    movie.setImdbId(tmdbMovie.imdbId());
    movie.setTagline(tmdbMovie.tagline());
    movie.setHomepage(tmdbMovie.homepage());
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
