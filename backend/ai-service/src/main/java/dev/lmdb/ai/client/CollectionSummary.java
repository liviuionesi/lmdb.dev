package dev.lmdb.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One movie collection (franchise) as movie-service lists it in a collection search.
 *
 * @param id TMDB collection id
 * @param name the collection's name
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CollectionSummary(Long id, String name) {}
