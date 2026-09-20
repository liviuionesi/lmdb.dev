package dev.lmdb.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One genre as movie-service lists it.
 *
 * @param id TMDB genre id
 * @param name the genre's name, such as "Action"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenreSummary(Long id, String name) {}
