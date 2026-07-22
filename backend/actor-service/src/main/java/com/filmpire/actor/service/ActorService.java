package com.filmpire.actor.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.filmpire.actor.dto.ActorDtos.ActorDto;
import com.filmpire.actor.dto.ActorDtos.ActorSearchResponse;
import com.filmpire.actor.dto.ActorDtos.ActorSummaryDto;
import com.filmpire.actor.dto.ActorDtos.FilmographyEntryDto;
import com.filmpire.actor.facade.PersonFacadeService;
import com.filmpire.actor.model.Actor;
import com.filmpire.actor.repository.ActorRepository;
import com.filmpire.shared.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Native typed actor API (ARCHITECTURE.md §3.6, issue #18).
 *
 * <p>Single-fetch-path design: this service does NOT call TMDB directly.
 * All data flows through {@link PersonFacadeService}'s raw read-through
 * cache and is PARSED here into typed DTOs — so the facade and the native
 * API can never disagree about the underlying data, and every native call
 * warms the same cache the React app uses.</p>
 *
 * <p>As a side effect of {@link #getActor}, core profile fields are
 * upserted into the typed {@code actors} table, giving the service a
 * queryable local dataset that grows with use (mirrors the save-through
 * philosophy of ADR-003).</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ActorService {

    private final PersonFacadeService personFacadeService;
    private final ActorRepository actorRepository;
    private final ObjectMapper objectMapper;

    /**
     * Fetches an actor profile, upserting the typed row.
     *
     * @param tmdbId TMDB person id
     * @return typed actor profile
     */
    @Transactional
    public ActorDto getActor(Long tmdbId) {
        // 1. Raw person document via the shared read-through path.
        JsonNode person = parse(personFacadeService.getRaw("person/" + tmdbId, none()));

        // 2. Upsert the typed projection (queryable local dataset).
        Actor actor = Actor.builder()
            .tmdbId(tmdbId)
            .name(person.path("name").asString())
            .biography(person.path("biography").asString(""))
            .birthDate(parseDate(person.path("birthday").asString(null)))
            .birthPlace(person.path("place_of_birth").asString(null))
            .profilePath(person.path("profile_path").asString(null))
            .popularity(person.path("popularity").asDouble())
            .syncedAt(LocalDateTime.now())
            .build();
        actorRepository.save(actor);

        log.debug("Actor {} ('{}') synced to typed store", tmdbId, actor.getName());

        // 3. Typed DTO from the same parsed document.
        return new ActorDto(
            tmdbId,
            actor.getName(),
            actor.getBiography(),
            actor.getBirthDate(),
            actor.getBirthPlace(),
            actor.getProfilePath(),
            actor.getPopularity()
        );
    }

    /**
     * Fetches an actor's filmography from TMDB's {@code movie_credits},
     * newest first.
     *
     * @param tmdbId TMDB person id
     * @return cast credits sorted by release date descending
     */
    @Transactional(readOnly = true)
    public List<FilmographyEntryDto> getFilmography(Long tmdbId) {
        JsonNode credits = parse(
            personFacadeService.getRaw("person/" + tmdbId + "/movie_credits", none()));

        List<FilmographyEntryDto> entries = new ArrayList<>();
        credits.path("cast").forEach(entry -> entries.add(new FilmographyEntryDto(
            entry.path("id").asLong(),
            entry.path("title").asString(),
            entry.path("character").asString(null),
            entry.path("release_date").asString(""),
            entry.path("poster_path").asString(null),
            entry.path("vote_average").asDouble()
        )));

        // Newest releases first; undated entries sink to the end.
        entries.sort((a, b) -> b.releaseDate().compareTo(a.releaseDate()));
        return entries;
    }

    /**
     * Searches actors by name via TMDB's person search.
     *
     * @param query free-text name query
     * @param page  TMDB page (1-based)
     * @return paged summaries (transient — search results are not persisted)
     */
    @Transactional(readOnly = true)
    public ActorSearchResponse search(String query, int page) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("query", query);
        params.add("page", String.valueOf(page));

        JsonNode result = parse(personFacadeService.getRaw("search/person", params));

        List<ActorSummaryDto> actors = new ArrayList<>();
        result.path("results").forEach(person -> actors.add(new ActorSummaryDto(
            person.path("id").asLong(),
            person.path("name").asString(),
            person.path("profile_path").asString(null),
            person.path("popularity").asDouble()
        )));

        return new ActorSearchResponse(
            result.path("page").asInt(page),
            result.path("total_pages").asInt(0),
            result.path("total_results").asLong(0),
            actors
        );
    }

    /**
     * Parses a raw TMDB JSON body.
     *
     * @param json raw body from the facade
     * @return parsed tree
     * @throws ServiceUnavailableException if TMDB returned unparseable JSON
     *                                     (never expected; indicates an
     *                                     upstream contract break)
     */
    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("Unparseable TMDB response: {}", e.getMessage());
            throw new ServiceUnavailableException("TMDB returned an unreadable response");
        }
    }

    /**
     * Parses TMDB's {@code yyyy-MM-dd} date strings, tolerating null/empty.
     *
     * @param value raw date string, may be null or empty
     * @return parsed date or null
     */
    private static LocalDate parseDate(String value) {
        return (value == null || value.isBlank()) ? null : LocalDate.parse(value);
    }

    /**
     * Convenience empty param map.
     *
     * @return empty multi-value map
     */
    private static MultiValueMap<String, String> none() {
        return new LinkedMultiValueMap<>();
    }
}
