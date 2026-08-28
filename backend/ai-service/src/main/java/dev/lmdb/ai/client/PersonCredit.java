package dev.lmdb.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

/**
 * The subset of actor-service's cast/crew credit fields (#203, ADR-020) this feature needs — one
 * shape for both: actor-service's cast credits ({@code FilmographyEntryDto}, dropping {@code
 * character}) and crew credits ({@code CrewCreditDto}, dropping {@code job}/{@code department}).
 * Aggregation only needs a credit's movie identity to build/intersect movie-id sets, never the
 * role-specific fields — those already drove which actor-service endpoint was called ({@code
 * /movies} for cast, {@code /crew?job=} for a specific crew role). Deliberately partial, like
 * {@link CandidateMovie}/{@link MovieListItem}; {@link JsonIgnoreProperties} tolerates the fields
 * this feature ignores.
 *
 * @param movieId TMDB movie id
 * @param title movie title
 * @param releaseDate release date string as actor-service serves it, may be empty
 * @param posterPath TMDB poster path, may be null
 * @param voteAverage TMDB vote average
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonCredit(
    Long movieId, String title, String releaseDate, String posterPath, Double voteAverage)
    implements Serializable {}
