package dev.lmdb.ai.client;

import dev.lmdb.shared.dto.PageResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls movie-service's own persisted catalog (via Eureka/{@code lb://}), never TMDB directly:
 * popular movies for recommendation candidates, title search, movie discovery by year and genre,
 * the genre list, and franchise (collection) search and details. movie-service fetches from TMDB
 * and saves what it fetches, so these calls also fill the local catalog.
 */
@Component
@Slf4j
public class MovieCatalogClient {

  /** How many movie-detail requests may run at the same time. */
  private static final int DETAILS_PARALLELISM = 8;

  private final RestClient restClient;

  /**
   * @param movieServiceRestClient the load-balanced client from {@link
   *     dev.lmdb.ai.config.RestClientConfig}, already resolving {@code lb://movie-service} via
   *     Eureka — explicitly qualified since {@link dev.lmdb.ai.config.RestClientConfig} now
   *     declares a second named {@link RestClient} bean for actor-service (#203)
   */
  public MovieCatalogClient(
      @Qualifier("movieServiceRestClient") RestClient movieServiceRestClient) {
    this.restClient = movieServiceRestClient;
  }

  /**
   * Fetches a page of currently popular movies as the candidate pool for recommendations.
   *
   * @param count how many candidates to request
   * @return the candidate movies, or an empty list if movie-service is unreachable (recommendations
   *     degrade gracefully rather than failing the whole request)
   */
  public List<CandidateMovie> fetchCandidates(int count) {
    try {
      PageResponse<CandidateMovie> page =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/api/v1/movies/popular")
                          .queryParam("page", 1)
                          .queryParam("size", count)
                          .build())
              .retrieve()
              .body(new ParameterizedTypeReference<PageResponse<CandidateMovie>>() {});
      return page == null || page.getContent() == null ? List.of() : page.getContent();
    } catch (Exception e) {
      log.warn(
          "movie-service unreachable while fetching recommendation candidates: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Delegates a plain-title query to movie-service's existing title search, unchanged (#198 AC3,
   * #203 AC4) — this client applies no filtering or ranking of its own.
   *
   * @param query the literal title text
   * @param count how many results to request
   * @return matching movies, most relevant first as movie-service ranks them; empty if
   *     movie-service is unreachable, degrading rather than failing the whole search request
   */
  public List<MovieListItem> searchByTitle(String query, int count) {
    try {
      PageResponse<MovieListItem> page =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/api/v1/movies/search")
                          .queryParam("query", query)
                          .queryParam("page", 1)
                          .queryParam("size", count)
                          .build())
              .retrieve()
              .body(new ParameterizedTypeReference<PageResponse<MovieListItem>>() {});
      return page == null || page.getContent() == null ? List.of() : page.getContent();
    } catch (Exception e) {
      log.warn("movie-service unreachable while searching by title: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Finds a movie collection (franchise) by name and returns the best match's id.
   *
   * @param name the franchise name, such as "James Bond"
   * @return the id of the first collection movie-service lists, or empty if none match or
   *     movie-service is unreachable
   */
  public Optional<Long> findCollectionId(String name) {
    try {
      List<CollectionSummary> found =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/api/v1/movies/collections/search")
                          .queryParam("query", name)
                          .build())
              .retrieve()
              .body(new ParameterizedTypeReference<List<CollectionSummary>>() {});
      return found == null || found.isEmpty()
          ? Optional.empty()
          : Optional.ofNullable(found.get(0).id());
    } catch (Exception e) {
      log.warn("movie-service unreachable while searching collections: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Fetches the movies in a collection (franchise).
   *
   * @param collectionId TMDB collection id
   * @return the collection's movies with their release dates; empty if movie-service is unreachable
   */
  public List<MovieListItem> fetchCollectionMovies(Long collectionId) {
    try {
      CollectionMovies collection =
          restClient
              .get()
              .uri("/api/v1/movies/collections/{id}", collectionId)
              .retrieve()
              .body(CollectionMovies.class);
      return collection == null || collection.movies() == null ? List.of() : collection.movies();
    } catch (Exception e) {
      log.warn("movie-service unreachable while fetching a collection: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Looks up a genre id by name.
   *
   * @param genreName a genre such as "Action"; compared without regard to case
   * @return the genre's id, or empty if movie-service has no genre with that name or is unreachable
   */
  public Optional<Long> findGenreId(String genreName) {
    try {
      List<GenreSummary> genres =
          restClient
              .get()
              .uri("/api/v1/genres")
              .retrieve()
              .body(new ParameterizedTypeReference<List<GenreSummary>>() {});
      if (genres == null) {
        return Optional.empty();
      }
      return genres.stream()
          .filter(genre -> genre.name() != null && genre.name().equalsIgnoreCase(genreName))
          .map(GenreSummary::id)
          .findFirst();
    } catch (Exception e) {
      log.warn("movie-service unreachable while listing genres: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Discovers movies by release-year range and genre, most popular first.
   *
   * @param yearFrom inclusive range start, or {@code null} for no lower bound
   * @param yearTo inclusive range end, or {@code null} for no upper bound
   * @param genreId a genre id, or {@code null} for any genre
   * @param count how many movies to request
   * @return the movies with their release dates; empty if movie-service is unreachable
   */
  public List<MovieListItem> discover(Integer yearFrom, Integer yearTo, Long genreId, int count) {
    try {
      PageResponse<MovieListItem> page =
          restClient
              .get()
              .uri(
                  uriBuilder -> {
                    uriBuilder
                        .path("/api/v1/movies/discover")
                        .queryParam("page", 1)
                        .queryParam("size", count);
                    // 1. Attach only the constraints the caller gave; an absent parameter means
                    //    "no limit on this side" to movie-service.
                    if (yearFrom != null) {
                      uriBuilder.queryParam("yearFrom", yearFrom);
                    }
                    if (yearTo != null) {
                      uriBuilder.queryParam("yearTo", yearTo);
                    }
                    if (genreId != null) {
                      uriBuilder.queryParam("genreId", genreId);
                    }
                    return uriBuilder.build();
                  })
              .retrieve()
              .body(new ParameterizedTypeReference<PageResponse<MovieListItem>>() {});
      return page == null || page.getContent() == null ? List.of() : page.getContent();
    } catch (Exception e) {
      log.warn("movie-service unreachable while discovering movies: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Fetches the details of several movies at once. movie-service serves a movie from its own
   * database when it has one, and fetches and saves it from TMDB when it does not.
   *
   * <p>At most {@link #DETAILS_PARALLELISM} requests run at the same time, so a long list does not
   * flood movie-service or TMDB.
   *
   * @param movieIds TMDB movie ids
   * @return the details of every movie that could be fetched, in the order of {@code movieIds}; a
   *     movie that fails is left out
   */
  public List<MovieDetails> fetchMovieDetails(Collection<Long> movieIds) {
    Semaphore permits = new Semaphore(DETAILS_PARALLELISM);
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Optional<MovieDetails>>> pending =
          movieIds.stream().map(id -> executor.submit(() -> fetchOne(id, permits))).toList();
      List<MovieDetails> details = new ArrayList<>();
      for (Future<Optional<MovieDetails>> future : pending) {
        future.get().ifPresent(details::add);
      }
      return details;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return List.of();
    } catch (ExecutionException e) {
      log.warn("Fetching movie details failed: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Fetches one movie's details, waiting for a free slot first.
   *
   * @param movieId TMDB movie id
   * @param permits limits how many requests run at once
   * @return the details, or empty if the request failed
   * @throws InterruptedException if the thread is interrupted while waiting for a slot
   */
  private Optional<MovieDetails> fetchOne(Long movieId, Semaphore permits)
      throws InterruptedException {
    permits.acquire();
    try {
      return Optional.ofNullable(
          restClient.get().uri("/api/v1/movies/{id}", movieId).retrieve().body(MovieDetails.class));
    } catch (Exception e) {
      log.warn("Could not fetch details for movie {}: {}", movieId, e.getMessage());
      return Optional.empty();
    } finally {
      permits.release();
    }
  }
}
