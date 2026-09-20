package dev.lmdb.movie.dto;

import java.io.Serializable;
import java.util.List;

/**
 * A movie collection (franchise) with the movies in it.
 *
 * @param id TMDB collection id
 * @param name the collection's name
 * @param movies the movies in the collection, in the same shape as any movie list
 */
public record CollectionDto(Long id, String name, List<MovieListDto> movies)
    implements Serializable {}
