package dev.lmdb.actor.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;

/**
 * TMDB API response for a person's {@code movie_credits} — the cast side backs the actor's
 * filmography.
 *
 * @param id TMDB person id
 * @param cast list of movie credits in which this person appears as cast
 */
public record TmdbPersonMovieCreditsResponse(Long id, List<TmdbCastCredit> cast)
    implements Serializable {

  /**
   * A single cast credit record from TMDB's movie_credits response.
   *
   * @param id TMDB movie id
   * @param title movie title
   * @param character character name played by the actor
   * @param releaseDate movie release date (YYYY-MM-DD string)
   * @param posterPath TMDB poster image CDN path
   * @param voteAverage community vote average
   */
  public record TmdbCastCredit(
      Long id,
      String title,
      String character,
      @JsonProperty("release_date") String releaseDate,
      @JsonProperty("poster_path") String posterPath,
      @JsonProperty("vote_average") Double voteAverage)
      implements Serializable {}
}
