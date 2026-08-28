package dev.lmdb.ai.dto;

/**
 * One movie in a natural-language search result (#203) — the same shape whether it came from the
 * plain-title fallback (movie-service's {@code /search}) or structured-filter aggregation
 * (actor-service credit data), so the frontend renders one result list regardless of which path
 * produced it (#198 AC3's "no query-shape branching in the frontend," carried through to
 * execution).
 *
 * @param movieId TMDB movie id
 * @param title movie title
 * @param overview short synopsis, may be empty (actor-service credit data doesn't carry one)
 * @param releaseDate release date string as the source service serves it, may be empty
 * @param posterPath TMDB poster path, may be null
 * @param voteAverage TMDB vote average
 */
public record SearchResultMovieDto(
    Long movieId,
    String title,
    String overview,
    String releaseDate,
    String posterPath,
    Double voteAverage) {}
