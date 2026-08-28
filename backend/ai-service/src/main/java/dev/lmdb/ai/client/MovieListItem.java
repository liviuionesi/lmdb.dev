package dev.lmdb.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

/**
 * The subset of movie-service's {@code MovieListDto} fields the natural-language search feature
 * (#203) needs — both movie-service's {@code /search} (plain-title fallback) and {@code /discover}
 * (year-range resolution) return this same list shape, so one record covers both call sites.
 * Deliberately partial, like {@link CandidateMovie}: each service owns its own contract, and {@link
 * JsonIgnoreProperties} tolerates the rest of movie-service's response fields this feature doesn't
 * need (backdropPath, voteCount, genres, popularity, adult).
 *
 * @param tmdbId TMDB movie id
 * @param title movie title
 * @param overview short synopsis
 * @param releaseDate release date, {@code YYYY-MM-DD} as movie-service serializes it
 * @param posterPath TMDB poster image CDN path
 * @param voteAverage community vote average
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MovieListItem(
    Long tmdbId,
    String title,
    String overview,
    String releaseDate,
    String posterPath,
    Double voteAverage)
    implements Serializable {}
