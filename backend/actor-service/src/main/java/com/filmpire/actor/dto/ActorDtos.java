package com.filmpire.actor.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable response records for the native {@code /api/v1/actors} API
 * (records-only rule, ARCHITECTURE.md Appendix B).
 */
public final class ActorDtos {

    /** Not instantiable — pure record container. */
    private ActorDtos() {
    }

    /**
     * Full actor profile.
     *
     * @param tmdbId             TMDB person id
     * @param name               actor's name
     * @param biography          biography text (may be empty)
     * @param birthDate          birth date, may be null
     * @param birthPlace         birthplace, may be null
     * @param profilePath        TMDB profile image path, may be null
     * @param popularity         TMDB popularity score
     * @param alsoKnownAs        alternate names/aliases, may be empty
     * @param knownForDepartment TMDB's primary department (e.g. "Acting"), may be null
     * @param gender             TMDB gender code (0=unspecified, 1=female, 2=male, 3=non-binary)
     * @param imdbId             IMDB id, may be null
     * @param homepage           personal/official homepage, may be null
     * @param adult              adult-content flag as TMDB reports it
     */
    public record ActorDto(
        Long tmdbId,
        String name,
        String biography,
        LocalDate birthDate,
        String birthPlace,
        String profilePath,
        Double popularity,
        List<String> alsoKnownAs,
        String knownForDepartment,
        Integer gender,
        String imdbId,
        String homepage,
        Boolean adult
    ) {
    }

    /**
     * Compact actor reference used in search results.
     *
     * @param tmdbId      TMDB person id
     * @param name        actor's name
     * @param profilePath TMDB profile image path, may be null
     * @param popularity  TMDB popularity score
     */
    public record ActorSummaryDto(
        Long tmdbId,
        String name,
        String profilePath,
        Double popularity
    ) {
    }

    /**
     * One movie in an actor's filmography (from TMDB movie_credits cast).
     *
     * @param movieId     TMDB movie id (hydrate details via the TMDB facade)
     * @param title       movie title
     * @param character   character played, may be null
     * @param releaseDate release date string as TMDB serves it, may be empty
     * @param posterPath  TMDB poster path, may be null
     * @param voteAverage TMDB vote average
     */
    public record FilmographyEntryDto(
        Long movieId,
        String title,
        String character,
        String releaseDate,
        String posterPath,
        Double voteAverage
    ) {
    }

    /**
     * Paged search result.
     *
     * @param page         current page (TMDB 1-based)
     * @param totalPages   total pages reported by TMDB
     * @param totalResults total hits reported by TMDB
     * @param results      actors on this page
     */
    public record ActorSearchResponse(
        int page,
        int totalPages,
        long totalResults,
        List<ActorSummaryDto> results
    ) {
    }
}
