package dev.lmdb.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lmdb.ai.dto.OscarCategory;
import dev.lmdb.ai.dto.SearchSort;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link QueryModifiers}, which reads relative years, sort order, "top N", a minimum rating
 * and an Oscar category straight from the query text. The date is fixed, so every "last N years"
 * answer is the same on every day the test runs.
 */
@DisplayName("QueryModifiers (reading years, sorting, limits and awards from the text)")
class QueryModifiersTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 20);

  /**
   * Reads a query with today fixed.
   *
   * @param query the query as typed
   * @return the modifiers it carries
   */
  private static QueryModifiers read(String query) {
    return QueryModifiers.extract(query.toLowerCase(), TODAY);
  }

  // ------------------------------------------------------------------------- relative years

  /** "The last 20 years" from September 2026 starts in 2006 and has no end. */
  @Test
  @DisplayName("reads 'the last 20 years' as a start year and no end year")
  void lastNYears() {
    QueryModifiers modifiers = read("Tom Cruise movies from the last 20 years");

    assertThat(modifiers.yearFrom()).isEqualTo(2006);
    assertThat(modifiers.yearTo()).isNull();
  }

  /** "Past 5 years" means the same as "last 5 years". */
  @Test
  @DisplayName("reads 'the past 5 years' the same way")
  void pastNYears() {
    assertThat(read("horror movies from the past 5 years").yearFrom()).isEqualTo(2021);
  }

  /** "This year" is one calendar year. */
  @Test
  @DisplayName("reads 'this year' as the current year")
  void thisYear() {
    QueryModifiers modifiers = read("movies released this year");

    assertThat(modifiers.yearFrom()).isEqualTo(2026);
    assertThat(modifiers.yearTo()).isEqualTo(2026);
  }

  /** "Last year" is one calendar year, the one before this. */
  @Test
  @DisplayName("reads 'last year' as the previous year")
  void lastYear() {
    QueryModifiers modifiers = read("best movies of last year");

    assertThat(modifiers.yearFrom()).isEqualTo(2025);
    assertThat(modifiers.yearTo()).isEqualTo(2025);
  }

  /** A decade is a fixed range, whatever the model said. */
  @Test
  @DisplayName("reads decades such as the 2010s, the 1990s and the 80s")
  void decades() {
    assertThat(read("Leonardo DiCaprio movies in the 2010s").yearFrom()).isEqualTo(2010);
    assertThat(read("Leonardo DiCaprio movies in the 2010s").yearTo()).isEqualTo(2019);
    assertThat(read("comedies of the 1990s").yearFrom()).isEqualTo(1990);
    assertThat(read("comedies of the 1990s").yearTo()).isEqualTo(1999);
    assertThat(read("action movies from the 80s").yearFrom()).isEqualTo(1980);
    assertThat(read("action movies from the 80's").yearTo()).isEqualTo(1989);
    assertThat(read("indie movies of the 00s").yearFrom()).isEqualTo(2000);
    assertThat(read("indie movies of the 00s").yearTo()).isEqualTo(2009);
  }

  /** A query with no relative time leaves the years to the model and the other rules. */
  @Test
  @DisplayName("reads no years when the query has no relative time")
  void noRelativeYears() {
    QueryModifiers modifiers = read("movies from 2015 to 2020");

    assertThat(modifiers.yearFrom()).isNull();
    assertThat(modifiers.yearTo()).isNull();
  }

  // ------------------------------------------------------------------------------- sorting

  /** "Sorted by rating" and "highest rated" both ask for the best-rated movies first. */
  @Test
  @DisplayName("reads rating words as a sort by rating")
  void ratingSort() {
    assertThat(read("Tom Cruise movies sorted by rating").sortBy()).isEqualTo(SearchSort.RATING);
    assertThat(read("highest rated comedies").sortBy()).isEqualTo(SearchSort.RATING);
    assertThat(read("best action movies from 2015 to 2020").sortBy()).isEqualTo(SearchSort.RATING);
  }

  /** Revenue words ask for the biggest earners first. */
  @Test
  @DisplayName("reads revenue words as a sort by revenue")
  void revenueSort() {
    assertThat(read("Tom Cruise movies sorted by revenue").sortBy()).isEqualTo(SearchSort.REVENUE);
    assertThat(read("highest grossing Harry Potter movies").sortBy()).isEqualTo(SearchSort.REVENUE);
    assertThat(read("box office hits by Nolan").sortBy()).isEqualTo(SearchSort.REVENUE);
  }

  /** Revenue wins over rating: "best" is only a word here, and "sorted by revenue" is the ask. */
  @Test
  @DisplayName("prefers revenue over rating when both words appear")
  void revenueBeatsRating() {
    assertThat(read("best action movies sorted by revenue").sortBy()).isEqualTo(SearchSort.REVENUE);
  }

  /** Date words ask for release order. */
  @Test
  @DisplayName("reads newest and oldest as release-date sorts")
  void dateSorts() {
    assertThat(read("Star Wars movies sorted by release date newest first").sortBy())
        .isEqualTo(SearchSort.NEWEST);
    assertThat(read("latest Marvel movies").sortBy()).isEqualTo(SearchSort.NEWEST);
    assertThat(read("oldest Bond movies").sortBy()).isEqualTo(SearchSort.OLDEST);
    assertThat(read("movies sorted by chronological release descending").sortBy())
        .isEqualTo(SearchSort.NEWEST);
    assertThat(read("movies sorted by chronological release ascending").sortBy())
        .isEqualTo(SearchSort.OLDEST);
  }

  /** A query with no sort words keeps the source order. */
  @Test
  @DisplayName("reads no sort when the query has no sort words")
  void noSort() {
    assertThat(read("movies with Brad Pitt and Edward Norton").sortBy()).isNull();
  }

  /** "Best actor" is an award, not a request to sort by rating. */
  @Test
  @DisplayName("does not read 'best actor' as a sort by rating")
  void bestActorIsNotARatingSort() {
    assertThat(read("movies that won the oscar for best actor").sortBy()).isNull();
  }

  // --------------------------------------------------------------------------------- limit

  /** "Top 10" and "5 best" both give a count. */
  @Test
  @DisplayName("reads 'top N' and 'N best' as a limit")
  void limits() {
    assertThat(read("top 10 highest rated comedies").limit()).isEqualTo(10);
    assertThat(read("the 5 best movies by Tarantino").limit()).isEqualTo(5);
    assertThat(read("top 5 highest rated movies directed by Quentin Tarantino").limit())
        .isEqualTo(5);
  }

  /** A number that is a count of years is not a limit. */
  @Test
  @DisplayName("does not read the 20 in 'last 20 years' as a limit")
  void yearsAreNotALimit() {
    assertThat(read("Tom Cruise movies from the last 20 years").limit()).isNull();
  }

  // -------------------------------------------------------------------------- minimum rating

  /** A rating threshold in either word order. */
  @Test
  @DisplayName("reads 'rated above 7' and 'over 7.5 stars' as a minimum rating")
  void minimumRating() {
    assertThat(read("Meryl Streep movies rated above 7").minRating()).isEqualTo(7.0);
    assertThat(read("comedies with a rating of at least 7.5").minRating()).isEqualTo(7.5);
    assertThat(read("movies over 8 stars").minRating()).isEqualTo(8.0);
  }

  /** A year after "over" is not a rating. */
  @Test
  @DisplayName("does not read a year as a minimum rating")
  void aYearIsNotARating() {
    assertThat(read("movies over 2000").minRating()).isNull();
  }

  // ----------------------------------------------------------------------------------- award

  /** Each Oscar category, said in the ways people say it. */
  @Test
  @DisplayName("reads the Oscar category")
  void oscarCategories() {
    assertThat(read("all the movies that won the oscar for the best actor").award())
        .isEqualTo(OscarCategory.BEST_ACTOR);
    assertThat(read("oscar best actress winners").award()).isEqualTo(OscarCategory.BEST_ACTRESS);
    assertThat(read("movies where someone won the oscar for best supporting actor").award())
        .isEqualTo(OscarCategory.BEST_SUPPORTING_ACTOR);
    assertThat(read("academy award for best supporting actress").award())
        .isEqualTo(OscarCategory.BEST_SUPPORTING_ACTRESS);
    assertThat(read("oscar best director winners").award()).isEqualTo(OscarCategory.BEST_DIRECTOR);
    assertThat(read("oscar best picture winners from the last 20 years").award())
        .isEqualTo(OscarCategory.BEST_PICTURE);
  }

  /** "Oscar winners" alone means the top prize. */
  @Test
  @DisplayName("reads plain 'oscar winners' as best picture")
  void plainOscarWinners() {
    assertThat(read("oscar winning movies").award()).isEqualTo(OscarCategory.BEST_PICTURE);
  }

  /** "Best actor" with no award word could mean anything, so it is not an award query. */
  @Test
  @DisplayName("reads no award when the query has no award word")
  void noAwardWithoutAnAwardWord() {
    assertThat(read("movies with the best actor performances").award()).isNull();
    assertThat(read("Tom Hanks movies").award()).isNull();
  }
}
