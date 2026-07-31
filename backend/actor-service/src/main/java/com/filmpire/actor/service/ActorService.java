package com.filmpire.actor.service;

import com.filmpire.actor.client.TmdbPersonClient;
import com.filmpire.actor.client.dto.TmdbPersonImagesResponse;
import com.filmpire.actor.client.dto.TmdbPersonMovieCreditsResponse;
import com.filmpire.actor.client.dto.TmdbPersonResponse;
import com.filmpire.actor.client.dto.TmdbPersonSearchResponse;
import com.filmpire.actor.dto.ActorDtos.ActorDto;
import com.filmpire.actor.dto.ActorDtos.ActorImageDto;
import com.filmpire.actor.dto.ActorDtos.ActorSearchResponse;
import com.filmpire.actor.dto.ActorDtos.ActorSummaryDto;
import com.filmpire.actor.dto.ActorDtos.FilmographyEntryDto;
import com.filmpire.actor.dto.ActorDtos.FilmographyPageDto;
import com.filmpire.actor.mapper.ActorMapper;
import com.filmpire.actor.model.Actor;
import com.filmpire.actor.model.ActorProfileImage;
import com.filmpire.actor.repository.ActorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Native typed actor API (ARCHITECTURE.md §3.6, issue #18) and the backing
 * service for the TMDB-shaped facade — one persisted dataset behind both, as
 * of ADR-010 (supersedes ADR-003's raw-passthrough design).
 *
 * <p>Detail lookups are read-through/save-through against PostgreSQL (the
 * {@link Actor} table): a fetch maps TMDB's response into the typed entity
 * and saves it, and a later request for the same id is served locally, no
 * TMDB call. Search results are also upserted (lightweight stubs — name,
 * profile path, popularity only), so the dataset grows from any endpoint
 * that returns an actor. Filmography stays a live TMDB call on every
 * request: the movies it references live in movie-service's own database
 * (ADR-002), so there is nothing of actor-service's to persist there.</p>
 *
 * <p>The TMDB API key is no longer handled here — it is injected transparently
 * by the {@link org.springframework.web.client.RestClient} interceptor
 * configured in {@link com.filmpire.actor.client.TmdbClientConfig}, keeping
 * this class focused on orchestration rather than transport concerns.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ActorService {

    private final TmdbPersonClient tmdbPersonClient;
    private final ActorRepository actorRepository;
    private final ActorMapper actorMapper;

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
     * Core read-through/save-through lookup shared by the native API and the
     * facade: PostgreSQL first, TMDB on miss, save-through on fetch.
     *
     * <p>Results are cached in Redis ({@code actors} cache region) so that
     * repeated lookups for the same person skip both PostgreSQL and TMDB
     * entirely. The cache is evicted when an upsert modifies the entity
     * (see {@link #upsertFromSearchResult}).</p>
     *
     * @param tmdbId TMDB person id
     * @return the persisted (or freshly fetched-and-saved) actor entity
     */
    @Transactional
    @Cacheable(value = "actors", key = "#tmdbId")
    public Actor getOrFetchActorEntity(Long tmdbId) {
        return fetchActorEntity(tmdbId);
    }

    /**
     * Internal unannotated helper method for fetching actor entity to avoid proxy self-invocation.
     */
    private Actor fetchActorEntity(Long tmdbId) {
        // Consult local database first; if missing, fetch from upstream TMDB client and persist locally.
        return actorRepository.findById(tmdbId)
            .orElseGet(() -> {
                log.info("Actor {} not in PostgreSQL, fetching from TMDB", tmdbId);
                TmdbPersonResponse response = tmdbPersonClient.getPersonDetails(tmdbId);
                return convertAndSaveActor(response);
            });
    }

    /**
     * Fetches an actor's filmography from TMDB's {@code movie_credits},
     * newest release first.
     *
     * @param tmdbId TMDB person id
     * @return cast credits sorted by release date descending
     */
    public List<FilmographyEntryDto> getFilmography(Long tmdbId) {
        // Map raw cast credits to native DTO entries, substituting empty string for missing release dates.
        List<FilmographyEntryDto> entries = getFilmographyRaw(tmdbId).cast().stream()
            .map(c -> new FilmographyEntryDto(
                c.id(), c.title(), c.character(),
                c.releaseDate() != null ? c.releaseDate() : "",
                c.posterPath(), c.voteAverage()))
            .collect(Collectors.toCollection(ArrayList::new));

        // Newest releases first; undated entries sink to the end.
        entries.sort((a, b) -> b.releaseDate().compareTo(a.releaseDate()));
        return entries;
    }

    /**
     * Returns one page of an actor's filmography.
     *
     * <p>Paginated in memory on purpose: TMDB's {@code movie_credits} has no
     * page parameter — it returns every credit in one response — so the
     * facade must keep serving the unpaginated shape while the native API
     * still offers paging (issue #18). Requesting a page past the end yields
     * an empty page rather than an error, matching TMDB's list behavior.</p>
     *
     * @param tmdbId   TMDB person id
     * @param page     1-based page number
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
     * Returns an actor's profile images, read-through/save-through against
     * PostgreSQL like the profile itself: fetched from TMDB once, persisted on
     * the {@link Actor}, and served locally afterwards (ADR-010).
     *
     * <p>Transactional and mapped to DTOs before returning, so the EAGER
     * image collection is fully resolved inside the transaction.</p>
     *
     * @param tmdbId TMDB person id
     * @return the actor's profile-image references
     */
    @Transactional
    public List<ActorImageDto> getImages(Long tmdbId) {
        // Transform domain profile image entities into DTO representations inside the active transaction.
        return fetchImages(tmdbId).stream()
            .map(i -> new ActorImageDto(
                i.getFilePath(), i.getAspectRatio(), i.getHeight(),
                i.getWidth(), i.getIso6391(), i.getVoteAverage(), i.getVoteCount()))
            .toList();
    }

    /**
     * Core read-through/save-through image lookup shared by the native API and
     * the facade. An actor row with no persisted images triggers one TMDB
     * fetch, which is then saved onto the actor.
     *
     * <p>Results are cached in Redis ({@code actor-images} cache region).</p>
     *
     * @param tmdbId TMDB person id
     * @return persisted profile images (empty if TMDB has none)
     */
    @Transactional
    @Cacheable(value = "actor-images", key = "#tmdbId")
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
        List<ActorProfileImage> images = response.profiles() == null ? List.of()
            : response.profiles().stream()
                .map(p -> ActorProfileImage.builder()
                    .filePath(p.filePath())
                    .aspectRatio(p.aspectRatio())
                    .height(p.height())
                    .width(p.width())
                    .iso6391(p.iso6391())
                    .voteAverage(p.voteAverage())
                    .voteCount(p.voteCount())
                    .build())
                .toList();

        // Update the actor entity with the fetched images and save back to PostgreSQL.
        actor.setProfileImages(new ArrayList<>(images));
        actorRepository.save(actor);
        return actor.getProfileImages();
    }

    /**
     * Returns TMDB's currently-popular people. Like search, this is a live
     * ranking call — TMDB's popularity ordering isn't reimplemented here — but
     * every person it returns is upserted, so the local catalog grows.
     *
     * @param page TMDB page (1-based)
     * @return paged summaries
     */
    @Transactional
    public ActorSearchResponse getPopular(int page) {
        // Map raw TMDB popular response into native ActorSearchResponse envelope.
        return toSearchResponse(getPopularRaw(page), page);
    }

    /**
     * Facade-facing popular-people lookup — TMDB's own response shape. Every
     * result is upserted.
     *
     * @param page page number
     * @return raw TMDB popular-people response
     */
    @Transactional
    public TmdbPersonSearchResponse getPopularRaw(int page) {
        log.info("Fetching popular actors: page={}", page);
        // Call TMDB popular persons endpoint and upsert each returned actor summary.
        TmdbPersonSearchResponse response = tmdbPersonClient.getPopularPersons(page);
        response.results().forEach(this::upsertFromSearchResult);
        return response;
    }

    /**
     * Facade-facing filmography lookup — TMDB's own response shape. Always
     * live: the referenced movies are movie-service's data, not ours.
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
     * Searches actors by name via TMDB's person search. Every result is
     * upserted as a lightweight stub (name/profile/popularity only — a
     * search hit doesn't carry the full profile TMDB's detail endpoint does).
     *
     * @param query free-text name query
     * @param page  TMDB page (1-based)
     * @return paged summaries
     */
    @Transactional
    public ActorSearchResponse search(String query, int page) {
        // Execute raw TMDB search and map output to native search response DTO.
        return toSearchResponse(searchRaw(query, page), page);
    }

    /**
     * Maps TMDB's shared person-list envelope (search and popular use the same
     * one) into the native API's paged response, defaulting the paging fields
     * TMDB may omit.
     *
     * @param response    TMDB's person-list response
     * @param requestedPage page asked for, used when TMDB doesn't echo one back
     * @return native paged summaries
     */
    private ActorSearchResponse toSearchResponse(TmdbPersonSearchResponse response, int requestedPage) {
        // Convert list of TmdbPersonSummary items into ActorSummaryDto instances.
        List<ActorSummaryDto> actors = response.results().stream()
            .map(actorMapper::toSummaryDto)
            .toList();
        return new ActorSearchResponse(
            response.page() != null ? response.page() : requestedPage,
            response.totalPages() != null ? response.totalPages() : 0,
            response.totalResults() != null ? response.totalResults() : 0,
            actors
        );
    }

    /**
     * Facade-facing search — TMDB's own response shape. Every result is
     * upserted.
     *
     * @param query search query
     * @param page  page number
     * @return raw TMDB person-search response
     */
    @Transactional
    public TmdbPersonSearchResponse searchRaw(String query, int page) {
        log.info("Searching actors: query={}, page={}", query, page);
        // Call TMDB search endpoint and upsert each returned actor summary to database.
        TmdbPersonSearchResponse response = tmdbPersonClient.searchPersons(query, page);
        response.results().forEach(this::upsertFromSearchResult);
        return response;
    }

    /**
     * Upserts a person-list stub into PostgreSQL (used by both search and
     * popular). List results carry only a subset of the detail endpoint's
     * profile, so an existing, more-detailed row (biography, birth date, etc.
     * from a prior detail fetch) is updated in place rather than clobbered —
     * and fields the list omits are left untouched instead of nulled.
     *
     * <p>Evicts the actor from the Redis cache so that the next read-through
     * lookup serves the freshly-updated data.</p>
     *
     * @param summary a single result from a TMDB person-list endpoint
     */
    @CacheEvict(value = "actors", key = "#summary.id()")
    private void upsertFromSearchResult(TmdbPersonSearchResponse.TmdbPersonSummary summary) {
        // Retrieve existing actor entity if present to prevent clobbering existing detailed fields.
        Actor actor = actorRepository.findById(summary.id()).orElseGet(() -> {
            Actor fresh = new Actor();
            fresh.setTmdbId(summary.id());
            return fresh;
        });
        actor.setName(summary.name());
        actor.setProfilePath(summary.profilePath());
        actor.setPopularity(summary.popularity());
        if (summary.knownForDepartment() != null) {
            actor.setKnownForDepartment(summary.knownForDepartment());
        }
        if (summary.gender() != null) {
            actor.setGender(summary.gender());
        }
        if (summary.adult() != null) {
            actor.setAdult(summary.adult());
        }
        actor.setSyncedAt(LocalDateTime.now(Clock.systemUTC()));
        actorRepository.save(actor);
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
        Actor saved = actorRepository.save(actor);
        log.debug("Actor {} ('{}') synced to typed store", saved.getTmdbId(), saved.getName());
        return saved;
    }
}
