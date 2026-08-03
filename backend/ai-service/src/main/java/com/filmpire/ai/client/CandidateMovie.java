package com.filmpire.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

/**
 * The subset of movie-service's {@code MovieListDto} fields the recommendation prompt needs.
 * Deliberately partial (not a shared/copied DTO across the service boundary — each service owns its
 * own contract); {@link JsonIgnoreProperties} tolerates the rest of movie-service's response shape.
 * Implements {@link Serializable} because it fills the bounded type parameter of shared-library's
 * {@code PageResponse<T>}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CandidateMovie(Long tmdbId, String title, String overview, Double voteAverage)
    implements Serializable {}
