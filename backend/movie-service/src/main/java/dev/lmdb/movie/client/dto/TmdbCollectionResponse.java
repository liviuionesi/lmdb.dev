package dev.lmdb.movie.client.dto;

import java.io.Serializable;
import java.util.List;

/**
 * TMDB's reply to {@code /collection/{id}}: one movie franchise and the movies in it.
 *
 * @param id TMDB collection id
 * @param name the collection's name
 * @param parts the movies in the collection, in the same shape as any TMDB movie list
 */
public record TmdbCollectionResponse(
    Long id, String name, List<TmdbMovieListResponse.TmdbMovieItem> parts)
    implements Serializable {}
