package dev.lmdb.movie.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;

/**
 * TMDB's reply to {@code /search/collection}: the collections (movie franchises such as "James Bond
 * Collection") whose name matches a query.
 *
 * @param page the page number of this reply
 * @param results the matching collections, best match first
 */
public record TmdbCollectionSearchResponse(Integer page, List<TmdbCollectionItem> results)
    implements Serializable {

  /**
   * One collection in a search reply.
   *
   * @param id TMDB collection id
   * @param name the collection's name
   * @param posterPath poster image path, or {@code null}
   */
  public record TmdbCollectionItem(
      Long id, String name, @JsonProperty("poster_path") String posterPath)
      implements Serializable {}
}
