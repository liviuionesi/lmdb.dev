package dev.lmdb.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

/**
 * The subset of actor-service's person-search result fields (#203, ADR-020) needed to resolve a
 * free-text name (e.g. {@code personName} or a {@code collaborators} entry from #202's structured
 * filter) to a TMDB person id. Deliberately partial, like {@link CandidateMovie}; {@link
 * JsonIgnoreProperties} tolerates the rest of actor-service's {@code ActorSummaryDto} shape
 * (profilePath, popularity) this feature doesn't need.
 *
 * @param tmdbId TMDB person id
 * @param name the person's name, as actor-service/TMDB has it — kept only for logging, never used
 *     to re-match against the caller's free text
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonSummary(Long tmdbId, String name) implements Serializable {}
