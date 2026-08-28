package dev.lmdb.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.List;

/**
 * The subset of actor-service's paged person-search envelope ({@code ActorSearchResponse}) this
 * feature needs — just the results, ranked by actor-service/TMDB's own relevance ordering (#203).
 *
 * @param results matched people, most relevant first; empty if no match
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonSearchResult(List<PersonSummary> results) implements Serializable {}
