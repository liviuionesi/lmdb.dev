package dev.lmdb.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.lmdb.ai.client.ActorCatalogClient;
import dev.lmdb.ai.client.MovieCatalogClient;
import dev.lmdb.ai.client.PersonCredit;
import dev.lmdb.ai.dto.NaturalLanguageSearchResponseDto;
import dev.lmdb.ai.dto.QueryFilterRole;
import dev.lmdb.ai.dto.SearchResultMovieDto;
import dev.lmdb.ai.dto.StructuredQueryFilterDto;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests how {@link QueryAggregationService} turns a parsed filter into movies. The parser and both
 * catalog clients are Mockito mocks, so each test controls exactly which credits exist and checks
 * what the service keeps.
 */
@DisplayName("QueryAggregationService (filtering a person's credits)")
class QueryAggregationServiceTest {

  private static final long PERSON_ID = 31L;

  private QueryParsingService parser;
  private ActorCatalogClient actors;
  private MovieCatalogClient movies;
  private QueryAggregationService service;

  /** Builds the service on mocks; each test stubs the parsed filter and the credits it needs. */
  @BeforeEach
  void setUp() {
    parser = mock(QueryParsingService.class);
    actors = mock(ActorCatalogClient.class);
    movies = mock(MovieCatalogClient.class);
    service = new QueryAggregationService(parser, actors, movies);
    when(actors.findPersonId("Tom Hanks")).thenReturn(Optional.of(PERSON_ID));
  }

  /**
   * The person made films in and out of the range. Every film released inside the range must be
   * kept, including obscure ones. Before, the service kept only films that were also among the 200
   * most popular movies of those years, so 23 of Tom Hanks's 28 films from the 1990s disappeared.
   */
  @Test
  @DisplayName("keeps every credit released inside the year range, popular or not")
  void keepsEveryCreditInsideTheYearRange() {
    stubFilter(actorFilter(1990, 1999));
    stubCastCredits(
        credit(1L, "Big", "1988-06-03"),
        credit(2L, "Philadelphia", "1993-12-22"),
        credit(3L, "Forrest Gump", "1994-06-23"),
        credit(4L, "Obscure Short Film", "1997-01-01"),
        credit(5L, "The Green Mile", "1999-12-10"),
        credit(6L, "Cast Away", "2000-12-22"));

    List<String> titles = titlesOf(service.search("Tom Hanks movies in the 1990s"));

    assertThat(titles)
        .containsExactlyInAnyOrder(
            "Philadelphia", "Forrest Gump", "Obscure Short Film", "The Green Mile");
  }

  /** The range is inclusive on both ends: a film from the first or last year belongs in it. */
  @Test
  @DisplayName("includes the first and last year of the range")
  void yearRangeIsInclusive() {
    stubFilter(actorFilter(1990, 1999));
    stubCastCredits(
        credit(1L, "First Year", "1990-01-01"),
        credit(2L, "Last Year", "1999-12-31"),
        credit(3L, "Year Before", "1989-12-31"),
        credit(4L, "Year After", "2000-01-01"));

    assertThat(titlesOf(service.search("q"))).containsExactlyInAnyOrder("First Year", "Last Year");
  }

  /** "After 2000" gives only a start year. */
  @Test
  @DisplayName("handles a range with only a start year")
  void rangeWithOnlyAStartYear() {
    stubFilter(actorFilter(2000, null));
    stubCastCredits(
        credit(1L, "Old", "1999-05-01"),
        credit(2L, "New", "2010-05-01"),
        credit(3L, "Newer", "2024-05-01"));

    assertThat(titlesOf(service.search("q"))).containsExactlyInAnyOrder("New", "Newer");
  }

  /** "Before 1990" gives only an end year. */
  @Test
  @DisplayName("handles a range with only an end year")
  void rangeWithOnlyAnEndYear() {
    stubFilter(actorFilter(null, 1989));
    stubCastCredits(credit(1L, "Old", "1988-05-01"), credit(2L, "New", "1990-05-01"));

    assertThat(titlesOf(service.search("q"))).containsExactly("Old");
  }

  /**
   * A credit with no release date cannot be shown to be inside the range, so it is left out when
   * the query asks for a range. Without a range it is kept.
   */
  @Test
  @DisplayName("drops credits with no release date only when a range was asked for")
  void creditsWithoutADateAreDroppedOnlyForARangeQuery() {
    stubCastCredits(credit(1L, "Undated", ""), credit(2L, "Dated", "1995-01-01"));

    stubFilter(actorFilter(1990, 1999));
    assertThat(titlesOf(service.search("q"))).containsExactly("Dated");

    stubFilter(actorFilter(null, null));
    assertThat(titlesOf(service.search("q"))).containsExactlyInAnyOrder("Undated", "Dated");
  }

  /** The year check now reads dates from the credits, so movie-service is not asked to discover. */
  @Test
  @DisplayName("does not call movie-service's discover endpoint for a year range")
  void doesNotUseDiscoverForTheYearRange() {
    stubFilter(actorFilter(1990, 1999));
    stubCastCredits(credit(1L, "Philadelphia", "1993-12-22"));

    service.search("q");

    verify(movies, never()).discoverMovieIdsInYearRange(any(), any(), anyInt());
  }

  /**
   * Builds a filter for an actor named Tom Hanks with the given years.
   *
   * @param yearFrom first year, or null
   * @param yearTo last year, or null
   * @return the filter the parser would return
   */
  private static StructuredQueryFilterDto actorFilter(Integer yearFrom, Integer yearTo) {
    return new StructuredQueryFilterDto(
        "Tom Hanks", QueryFilterRole.ACTED, yearFrom, yearTo, List.of(), null, List.of(), null);
  }

  /**
   * Makes the parser return {@code filter} for any query.
   *
   * @param filter the parsed filter to return
   */
  private void stubFilter(StructuredQueryFilterDto filter) {
    when(parser.parse(any())).thenReturn(filter);
  }

  /**
   * Makes actor-service return these cast credits for the person.
   *
   * @param credits the person's cast credits
   */
  private void stubCastCredits(PersonCredit... credits) {
    when(actors.fetchCastCredits(PERSON_ID)).thenReturn(List.of(credits));
  }

  /**
   * Builds one cast credit.
   *
   * @param id movie id
   * @param title movie title
   * @param releaseDate release date as {@code yyyy-MM-dd}, or an empty string
   * @return the credit
   */
  private static PersonCredit credit(long id, String title, String releaseDate) {
    return new PersonCredit(id, title, releaseDate, null, 7.0);
  }

  /**
   * Lists the titles in a response.
   *
   * @param response the search response
   * @return the result titles, in order
   */
  private static List<String> titlesOf(NaturalLanguageSearchResponseDto response) {
    return response.results().stream().map(SearchResultMovieDto::title).toList();
  }
}
