package dev.lmdb.actor.service;

import dev.lmdb.actor.client.TmdbPersonClient;
import dev.lmdb.actor.client.dto.TmdbPersonImagesResponse;
import dev.lmdb.actor.client.dto.TmdbPersonMovieCreditsResponse;
import dev.lmdb.actor.client.dto.TmdbPersonResponse;
import dev.lmdb.actor.client.dto.TmdbPersonSearchResponse;
import dev.lmdb.actor.dto.ActorDtos.ActorDto;
import dev.lmdb.actor.dto.ActorDtos.ActorImageDto;
import dev.lmdb.actor.dto.ActorDtos.ActorSearchResponse;
import dev.lmdb.actor.dto.ActorDtos.ActorSummaryDto;
import dev.lmdb.actor.dto.ActorDtos.FilmographyEntryDto;
import dev.lmdb.actor.dto.ActorDtos.FilmographyPageDto;
import dev.lmdb.actor.mapper.ActorMapper;
import dev.lmdb.actor.model.Actor;
import dev.lmdb.actor.model.ActorProfileImage;
import dev.lmdb.actor.repository.ActorRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Native typed actor API (ARCHITECTURE.md §3.6, issue #18) and the backing service for the
 * TMDB-shaped facade — one persisted dataset behind both, as of ADR-010 (supersedes ADR-003's
 * raw-passthrough design).
 *
 * <p>Detail lookups are read-through/save-through against PostgreSQL (the {@link Actor} table): a
 * fetch maps TMDB's response into the typed entity and saves it, and a later request for the same
 * id is served locally, no TMDB call. Search results are also upserted (lightweight stubs — name,
 * profile path, popularity only), so the dataset grows from any endpoint that returns an actor.
 * Filmography stays a live TMDB call on every request: the movies it references live in
 * movie-service's own database (ADR-002), so there is nothing of actor-service's to persist there.
 *
 * <p>The TMDB API key is no longer handled here — it is injected transparently by the {@link
 * org.springframework.web.client.RestClient} interceptor configured in {@link
 * dev.lmdb.actor.client.TmdbClientConfig}, keeping this class focused on orchestration rather than
 * transport concerns.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ActorService {

  private final TmdbPersonClient tmdbPersonClient;
  private final ActorRepository actorRepository;
  private final ActorMapper actorMapper;
  private final Clock clock;
  private final CacheManager cacheManager;

  /**
   * Fetches an actor profile, read-through/save-through against PostgreSQL.
   *
   * @param tmdbId TMDB person id
   * @return typed actor profile
   */
  @Transactional
  public ActorDto getActor(Long tmdbId) {
    // Retrieve or fetch actor entity via internal helper and convert to DTO response.
    return actorMapper.toDto(fetchActorEntity(tmdbId));
  }

  /**
   * Core read-through/save-through lookup shared by the native API and the facade: PostgreSQL
   * first, TMDB on miss, save-through on fetch.
   *
   * <p>Results are cached in Redis ({@code actors} cache region) so that repeated lookups for the
   * same person skip both PostgreSQL and TMDB entirely. The cache is evicted when an upsert
   * modifies the entity (see {@link #upsertFromSearchResult}). Single-flight {@code sync = true}
   * prevents cache stampedes on concurrent misses.
   *
   * @param tmdbId TMDB person id
   * @return the persisted (or freshly fetched-and-saved) actor entity
   */
  @Cacheable(value = "actors", key = "#tmdbId", sync = true)
  public Actor getOrFetchActorEntity(Long tmdbId) {
    return fetchActorEntity(tmdbId);
  }

  /**
   * Internal unannotated helper method for fetching actor entity to avoid proxy self-invocation.
   * Direct {@code this.} calls bypass the Spring AOP proxy, so {@code @Cacheable} is left to public
   * entry points.
   */
  private Actor fetchActorEntity(Long tmdbId) {
    // Consult local database first; if missing, fetch from upstream TMDB client and persist
    // locally.
    return actorRepository
        .findById(tmdbId)
        .orElseGet(
            () -> {
              log.info("Actor {} not in PostgreSQL, fetching from TMDB", tmdbId);
              TmdbPersonResponse response = tmdbPersonClient.getPersonDetails(tmdbId);
              return convertAndSaveActor(response);
            });
  }

  /**
   * Fetches an actor's filmography from TMDB's {@code movie_credits}, newest release first.
   *
   * @param tmdbId TMDB person id
   * @return cast credits sorted by release date descending
   */
  public List<FilmographyEntryDto> getFilmography(Long tmdbId) {
    // Map raw cast credits to native DTO entries via MapStruct and sort newest release first.
    List<FilmographyEntryDto> entries =
        new ArrayList<>(actorMapper.toFilmographyEntryDtos(getFilmographyRaw(tmdbId).cast()));

    // Newest releases first; undated entries sink to the end.
    entries.sort(
        Comparator.comparing(
            FilmographyEntryDto::releaseDate, Comparator.nullsLast(Comparator.reverseOrder())));
    return entries;
  }

  /**
   * Returns one page of an actor's filmography.
   *
   * <p>Paginated in memory on purpose: TMDB's {@code movie_credits} has no page parameter — it
   * returns every credit in one response — so the facade must keep serving the unpaginated shape
   * while the native API still offers paging (issue #18). Requesting a page past the end yields an
   * empty page rather than an error, matching TMDB's list behavior.
   *
   * @param tmdbId TMDB person id
   * @param page 1-based page number
   * @param pageSize credits per page
   * @return the requested page of credits, newest release first
   */
  public FilmographyPageDto getFilmographyPage(Long tmdbId, int page, int pageSize) {
    // Fetch full sorted filmography list and calculate in-memory subList boundaries.
    List<FilmographyEntryDto> all = getFilmography(tmdbId);
    int totalPages = (int) Math.ceil((double) all.size() / pageSize);
    int from = Math.min((page - 1) * pageSize, all.size());
    int to = Math.min(from + pageSize, all.size());
    return new FilmographyPageDto(page, totalPages, all.size(), all.subList(from, to));
  }

  /**
   * Returns an actor's profile images, read-through/save-through against PostgreSQL like the
   * profile itself: fetched from TMDB once, persisted on the {@link Actor}, and served locally
   * afterwards (ADR-010).
   *
   * <p>Transactional and mapped to DTOs before returning, so the EAGER image collection is fully
   * resolved inside the transaction.
   *
   * @param tmdbId TMDB person id
   * @return the actor's profile-image references
   */
  @Transactional
  public List<ActorImageDto> getImages(Long tmdbId) {
    // Transform domain profile image entities into DTO representations via MapStruct.
    return actorMapper.toImageDtos(fetchImages(tmdbId));
  }

  /**
   * Core read-through/save-through image lookup shared by the native API and the facade. An actor
   * row with no persisted images triggers one TMDB fetch, which is then saved onto the actor.
   *
   * <p>Results are cached in Redis ({@code actor-images} cache region) with single-flight {@code
   * sync = true}.
   *
   * @param tmdbId TMDB person id
   * @return persisted profile images (empty if TMDB has none)
   */
  @Cacheable(value = "actor-images", key = "#tmdbId", sync = true)
  public List<ActorProfileImage> getOrFetchImages(Long tmdbId) {
    return fetchImages(tmdbId);
  }

  /**
   * Internal unannotated helper method for fetching actor images to avoid proxy self-invocation.
   */
  private List<ActorProfileImage> fetchImages(Long tmdbId) {
    // Check if profile images are already persisted on the actor entity; fetch from TMDB if absent.
    Actor actor = fetchActorEntity(tmdbId);
    if (actor.getProfileImages() != null && !actor.getProfileImages().isEmpty()) {
      return actor.getProfileImages();
    }

    log.info("Actor {} has no persisted images, fetching from TMDB", tmdbId);
    TmdbPersonImagesResponse response = tmdbPersonClient.getPersonImages(tmdbId);
    List<ActorProfileImage> images =
        response.profiles() == null
            ? List.of()
            : actorMapper.toProfileImageEntities(response.profiles());

    // Update the actor entity with the fetched images and save back to PostgreSQL.
    actor.setProfileImages(new ArrayList<>(images));
    actorRepository.save(actor);
    return actor.getProfileImages();
  }

  /**
   * Returns TMDB's currently-popular people. Like search, this is a live ranking call — TMDB's
   * popularity ordering isn't reimplemented here — but every person it returns is upserted, so the
   * local catalog grows.
   *
   * @param page TMDB page (1-based)
   * @return paged summaries
   */
  public ActorSearchResponse getPopular(int page) {
    // Map raw TMDB popular response into native ActorSearchResponse envelope.
    return toSearchResponse(getPopularRaw(page), page);
  }

  /**
   * Facade-facing popular-people lookup — TMDB's own response shape. Every result is upserted.
   *
   * @param page page number
   * @return raw TMDB popular-people response
   */
  public TmdbPersonSearchResponse getPopularRaw(int page) {
    log.info("Fetching popular actors: page={}", page);
    // Call TMDB popular persons endpoint and upsert each returned actor summary.
    TmdbPersonSearchResponse response = tmdbPersonClient.getPopularPersons(page);
    response.results().forEach(this::upsertFromSearchResult);
    return response;
  }

  /**
   * Facade-facing filmography lookup — TMDB's own response shape. Always live: the referenced
   * movies are movie-service's data, not ours.
   *
   * @param tmdbId TMDB person id
   * @return raw TMDB movie-credits response
   */
  public TmdbPersonMovieCreditsResponse getFilmographyRaw(Long tmdbId) {
    log.info("Fetching filmography for person: {}", tmdbId);
    // Execute live TMDB API call to fetch movie credits for given person id.
    return tmdbPersonClient.getPersonMovieCredits(tmdbId);
  }

  /**
   * Searches actors by name via TMDB's person search. Every result is upserted as a lightweight
   * stub (name/profile/popularity only — a search hit doesn't carry the full profile TMDB's detail
   * endpoint does).
   *
   * @param query free-text name query
   * @param page TMDB page (1-based)
   * @return paged summaries
   */
  public ActorSearchResponse search(String query, int page) {
    // Execute raw TMDB search and map output to native search response DTO.
    return toSearchResponse(searchRaw(query, page), page);
  }

  /**
   * Maps TMDB's shared person-list envelope (search and popular use the same one) into the native
   * API's paged response, defaulting the paging fields TMDB may omit.
   *
   * @param response TMDB's person-list response
   * @param requestedPage page asked for, used when TMDB doesn't echo one back
   * @return native paged summaries
   */
  private ActorSearchResponse toSearchResponse(
      TmdbPersonSearchResponse response, int requestedPage) {
    // Convert list of TmdbPersonSummary items into ActorSummaryDto instances via MapStruct.
    List<ActorSummaryDto> actors = actorMapper.toSummaryDtos(response.results());
    return new ActorSearchResponse(
        response.page() != null ? response.page() : requestedPage,
        response.totalPages() != null ? response.totalPages() : 0,
        response.totalResults() != null ? response.totalResults() : 0,
        actors);
  }

  /**
   * Facade-facing search — TMDB's own response shape. Every result is upserted.
   *
   * @param query search query
   * @param page page number
   * @return raw TMDB person-search response
   */
  public TmdbPersonSearchResponse searchRaw(String query, int page) {
    log.info("Searching actors: query={}, page={}", query, page);
    // Call TMDB search endpoint and upsert each returned actor summary to database.
    TmdbPersonSearchResponse response = tmdbPersonClient.searchPersons(query, page);
    response.results().forEach(this::upsertFromSearchResult);
    return response;
  }

  /**
   * Upserts a person-list stub into PostgreSQL (used by both search and popular). List results
   * carry only a subset of the detail endpoint's profile, so an existing, more-detailed row
   * (biography, birth date, etc. from a prior detail fetch) is updated in place rather than
   * clobbered — and fields the list omits are left untouched instead of nulled.
   *
   * <p>Evicts the actor from the Redis cache so that the next read-through lookup serves the
   * freshly-updated data.
   *
   * @param summary a single result from a TMDB person-list endpoint
   */
  private void upsertFromSearchResult(TmdbPersonSearchResponse.TmdbPersonSummary summary) {
    // Retrieve existing actor entity if present to prevent clobbering existing detailed fields.
    Actor actor =
        actorRepository
            .findById(summary.id())
            .orElseGet(
                () -> {
                  Actor fresh = new Actor();
                  fresh.setTmdbId(summary.id());
                  return fresh;
                });

    // Partially update present summary attributes on the entity via MapStruct.
    actorMapper.updateEntityFromSummary(summary, actor);
    actor.setSyncedAt(LocalDateTime.now(clock));
    actorRepository.save(actor);

    // Evict cached entity from Redis so subsequent reads fetch the updated profile.
    evictActorCache(summary.id());
  }

  /**
   * Programmatically evicts an actor from Redis cache if a cache manager is active.
   *
   * @param tmdbId TMDB person id to evict
   */
  private void evictActorCache(Long tmdbId) {
    if (cacheManager != null) {
      Cache cache = cacheManager.getCache("actors");
      if (cache != null) {
        cache.evict(tmdbId);
      }
    }
  }

  /**
   * Converts a raw TMDB person response into an {@link Actor} entity and persists it to PostgreSQL.
   *
   * @param r the TMDB person response containing profile fields
   * @return the saved {@link Actor} entity
   */
  private Actor convertAndSaveActor(TmdbPersonResponse r) {
    // Map fields from TmdbPersonResponse record into new Actor entity via ActorMapper.
    Actor actor = actorMapper.toEntity(r);
    actor.setSyncedAt(LocalDateTime.now(clock));
    Actor saved = actorRepository.save(actor);
    log.debug("Actor {} ('{}') synced to typed store", saved.getTmdbId(), saved.getName());
    return saved;
  }
}
