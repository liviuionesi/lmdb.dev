package com.filmpire.actor.service;

import com.filmpire.actor.client.TmdbPersonClient;
import com.filmpire.actor.client.dto.TmdbPersonMovieCreditsResponse;
import com.filmpire.actor.client.dto.TmdbPersonResponse;
import com.filmpire.actor.client.dto.TmdbPersonSearchResponse;
import com.filmpire.actor.dto.ActorDtos.ActorDto;
import com.filmpire.actor.dto.ActorDtos.ActorSearchResponse;
import com.filmpire.actor.dto.ActorDtos.ActorSummaryDto;
import com.filmpire.actor.dto.ActorDtos.FilmographyEntryDto;
import com.filmpire.actor.model.Actor;
import com.filmpire.actor.repository.ActorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ActorService {

    private final TmdbPersonClient tmdbPersonClient;
    private final ActorRepository actorRepository;

    @Value("${tmdb.api.key}")
    private String tmdbApiKey;

    /**
     * Fetches an actor profile, read-through/save-through against PostgreSQL.
     *
     * @param tmdbId TMDB person id
     * @return typed actor profile
     */
    @Transactional
    public ActorDto getActor(Long tmdbId) {
        return toDto(getOrFetchActorEntity(tmdbId));
    }

    /**
     * Core read-through/save-through lookup shared by the native API and the
     * facade: PostgreSQL first, TMDB on miss, save-through on fetch.
     *
     * @param tmdbId TMDB person id
     * @return the persisted (or freshly fetched-and-saved) actor entity
     */
    @Transactional
    public Actor getOrFetchActorEntity(Long tmdbId) {
        return actorRepository.findById(tmdbId)
            .orElseGet(() -> {
                log.info("Actor {} not in PostgreSQL, fetching from TMDB", tmdbId);
                TmdbPersonResponse response = tmdbPersonClient.getPersonDetails(tmdbId, tmdbApiKey);
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
        List<FilmographyEntryDto> entries = getFilmographyRaw(tmdbId).cast().stream()
            .map(c -> new FilmographyEntryDto(
                c.id(), c.title(), c.character(),
                c.releaseDate() != null ? c.releaseDate() : "",
                c.posterPath(), c.voteAverage()))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        // Newest releases first; undated entries sink to the end.
        entries.sort((a, b) -> b.releaseDate().compareTo(a.releaseDate()));
        return entries;
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
        return tmdbPersonClient.getPersonMovieCredits(tmdbId, tmdbApiKey);
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
        TmdbPersonSearchResponse response = searchRaw(query, page);
        List<ActorSummaryDto> actors = response.results().stream()
            .map(p -> new ActorSummaryDto(p.id(), p.name(), p.profilePath(), p.popularity()))
            .toList();
        return new ActorSearchResponse(
            response.page() != null ? response.page() : page,
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
        TmdbPersonSearchResponse response = tmdbPersonClient.searchPersons(tmdbApiKey, query, page);
        response.results().forEach(this::upsertFromSearchResult);
        return response;
    }

    /**
     * Upserts a search-result stub into PostgreSQL. Search results only
     * carry name/profile/popularity, so an existing, more-detailed row
     * (biography, birth date, etc. from a prior detail fetch) is updated in
     * place rather than clobbered.
     *
     * @param summary a single result from TMDB's person search
     */
    private void upsertFromSearchResult(TmdbPersonSearchResponse.TmdbPersonSummary summary) {
        Actor actor = actorRepository.findById(summary.id()).orElseGet(() -> {
            Actor fresh = new Actor();
            fresh.setTmdbId(summary.id());
            return fresh;
        });
        actor.setName(summary.name());
        actor.setProfilePath(summary.profilePath());
        actor.setPopularity(summary.popularity());
        actor.setSyncedAt(LocalDateTime.now());
        actorRepository.save(actor);
    }

    private Actor convertAndSaveActor(TmdbPersonResponse r) {
        Actor actor = Actor.builder()
            .tmdbId(r.id())
            .name(r.name())
            .biography(r.biography() != null ? r.biography() : "")
            .birthDate(r.birthday())
            .birthPlace(r.placeOfBirth())
            .profilePath(r.profilePath())
            .popularity(r.popularity())
            .alsoKnownAs(r.alsoKnownAs())
            .knownForDepartment(r.knownForDepartment())
            .gender(r.gender())
            .imdbId(r.imdbId())
            .homepage(r.homepage())
            .adult(r.adult())
            .syncedAt(LocalDateTime.now())
            .build();

        Actor saved = actorRepository.save(actor);
        log.debug("Actor {} ('{}') synced to typed store", saved.getTmdbId(), saved.getName());
        return saved;
    }

    private ActorDto toDto(Actor a) {
        return new ActorDto(
            a.getTmdbId(), a.getName(), a.getBiography(), a.getBirthDate(), a.getBirthPlace(),
            a.getProfilePath(), a.getPopularity(), a.getAlsoKnownAs(), a.getKnownForDepartment(),
            a.getGender(), a.getImdbId(), a.getHomepage(), a.getAdult()
        );
    }
}
