package dev.lmdb.movie.dto;

import java.io.Serializable;

/**
 * One movie collection (a franchise such as "James Bond Collection") in a search result.
 *
 * @param id TMDB collection id
 * @param name the collection's name
 * @param posterPath poster image path, or {@code null}
 */
public record CollectionSummaryDto(Long id, String name, String posterPath)
    implements Serializable {}
