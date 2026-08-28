package dev.lmdb.actor.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;

/**
 * TMDB API response for a person's {@code movie_credits} — the cast side backs the actor's
 * filmography, the crew side backs director/producer/etc. credits (#217, ADR-020).
 *
 * @param id TMDB person id
 * @param cast list of movie credits in which this person appears as cast
 * @param crew list of movie credits in which this person appears as crew (director, producer,
 *     writer, etc. — distinguished by {@link TmdbCrewCredit#job()}/{@link
 *     TmdbCrewCredit#department()}); may be {@code null} for a person TMDB reports no crew credits
 *     for at all, distinct from an empty list
 */
public record TmdbPersonMovieCreditsResponse(
    Long id, List<TmdbCastCredit> cast, List<TmdbCrewCredit> crew) implements Serializable {

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

  /**
   * A single crew credit record from TMDB's movie_credits response — same shape as {@link
   * TmdbCastCredit} except {@code character} is replaced by {@code job}/{@code department}, per
   * ADR-020's "Prerequisite gaps" decision.
   *
   * @param id TMDB movie id
   * @param title movie title
   * @param job the specific job credited (e.g. "Director", "Producer", "Writer"), as TMDB reports
   *     it — case as TMDB sends it, not normalized here
   * @param department the TMDB department this job belongs to (e.g. "Directing", "Production")
   * @param releaseDate movie release date (YYYY-MM-DD string)
   * @param posterPath TMDB poster image CDN path
   * @param voteAverage community vote average
   */
  public record TmdbCrewCredit(
      Long id,
      String title,
      String job,
      String department,
      @JsonProperty("release_date") String releaseDate,
      @JsonProperty("poster_path") String posterPath,
      @JsonProperty("vote_average") Double voteAverage)
      implements Serializable {}
}
