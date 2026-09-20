package dev.lmdb.ai.dto;

/**
 * One movie in a natural-language search result. The shape is the same whichever source found the
 * movie (title search, a person's credits, a franchise, discover or an award list), so the frontend
 * renders one result list.
 *
 * @param movieId TMDB movie id
 * @param title movie title
 * @param overview short synopsis, may be empty (a person's credits do not carry one)
 * @param releaseDate release date string as the source service serves it, may be empty
 * @param posterPath TMDB poster path, may be null
 * @param voteAverage TMDB vote average
 * @param revenue box-office revenue in dollars, or {@code null} when it was not needed or not known
 */
public record SearchResultMovieDto(
    Long movieId,
    String title,
    String overview,
    String releaseDate,
    String posterPath,
    Double voteAverage,
    Long revenue) {

  /**
   * Builds a result with no revenue.
   *
   * @param movieId TMDB movie id
   * @param title movie title
   * @param overview short synopsis, may be empty
   * @param releaseDate release date string, may be empty
   * @param posterPath TMDB poster path, may be null
   * @param voteAverage TMDB vote average
   */
  public SearchResultMovieDto(
      Long movieId,
      String title,
      String overview,
      String releaseDate,
      String posterPath,
      Double voteAverage) {
    this(movieId, title, overview, releaseDate, posterPath, voteAverage, null);
  }
}
