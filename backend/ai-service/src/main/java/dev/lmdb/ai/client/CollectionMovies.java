package dev.lmdb.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A movie collection (franchise) with its movies, as movie-service returns it.
 *
 * @param id TMDB collection id
 * @param name the collection's name
 * @param movies the movies in the collection
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CollectionMovies(Long id, String name, List<MovieListItem> movies) {}
