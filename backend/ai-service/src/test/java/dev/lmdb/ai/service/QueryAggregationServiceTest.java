package dev.lmdb.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.lmdb.ai.client.ActorCatalogClient;
import dev.lmdb.ai.client.AwardsClient;
import dev.lmdb.ai.client.MovieCatalogClient;
import dev.lmdb.ai.client.MovieDetails;
import dev.lmdb.ai.client.MovieListItem;
import dev.lmdb.ai.client.PersonCredit;
import dev.lmdb.ai.dto.NaturalLanguageSearchResponseDto;
import dev.lmdb.ai.dto.OscarCategory;
import dev.lmdb.ai.dto.QueryFilterRole;
import dev.lmdb.ai.dto.SearchResultMovieDto;
import dev.lmdb.ai.dto.SearchSort;
import dev.lmdb.ai.dto.StructuredQueryFilterDto;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests how {@link QueryAggregationService} turns a parsed filter into movies. The parser, both
 * catalog clients and the relevance check are Mockito mocks, so each test controls exactly which
 * movies each lookup returns and checks what the service keeps, drops and reports.
 */
@DisplayName("QueryAggregationService (looking things up, combining and filtering)")
class QueryAggregationServiceTest {

  private static final long HANKS = 31L;
  private static final long CRAIG = 8784L;
  private static final long BOND_COLLECTION = 645L;

  private QueryParsingService parser;
  private ActorCatalogClient actors;
  private MovieCatalogClient movies;
  private RelevanceFilterService relevance;
  private AwardsClient awards;
  private QueryAggregationService service;

  /**
   * Builds the service on mocks. The relevance check keeps every movie by default; a test that
   * cares stubs it.
   */
  @BeforeEach
  void setUp() {
    parser = mock(QueryParsingService.class);
    actors = mock(ActorCatalogClient.class);
    movies = mock(MovieCatalogClient.class);
    relevance = mock(RelevanceFilterService.class);
    awards = mock(AwardsClient.class);
    service = new QueryAggregationService(parser, actors, movies, relevance, awards);
    when(actors.findPersonId("Tom Hanks")).thenReturn(Optional.of(HANKS));
    when(actors.findPersonId("Daniel Craig")).thenReturn(Optional.of(CRAIG));
    when(relevance.filter(anyString(), any())).thenAnswer(call -> call.getArgument(1));
  }

  // ---------------------------------------------------------------- years on a person's credits

  /**
   * The person made films in and out of the range. Every film released inside the range must be
   * kept, including obscure ones, because the credits carry their own release dates.
   */
  @Test
  @DisplayName("keeps every credit released inside the year range, popular or not")
  void keepsEveryCreditInsideTheYearRange() {
    stubFilter(actorFilter(1990, 1999));
    stubCastCredits(
        HANKS,
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
        HANKS,
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
        HANKS,
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
    stubCastCredits(HANKS, credit(1L, "Old", "1988-05-01"), credit(2L, "New", "1990-05-01"));

    assertThat(titlesOf(service.search("q"))).containsExactly("Old");
  }

  /**
   * A credit with no release date cannot be shown to be inside the range, so it is left out when
   * the query asks for a range. Without a range it is kept.
   */
  @Test
  @DisplayName("drops credits with no release date only when a range was asked for")
  void creditsWithoutADateAreDroppedOnlyForARangeQuery() {
    stubCastCredits(HANKS, credit(1L, "Undated", ""), credit(2L, "Dated", "1995-01-01"));

    stubFilter(actorFilter(1990, 1999));
    assertThat(titlesOf(service.search("q"))).containsExactly("Dated");

    stubFilter(actorFilter(null, null));
    assertThat(titlesOf(service.search("q"))).containsExactlyInAnyOrder("Undated", "Dated");
  }

  /** The year check reads dates from the credits, so movie-service is not asked to discover. */
  @Test
  @DisplayName("does not call discover when a person anchors the query")
  void doesNotUseDiscoverWhenAPersonAnchorsTheQuery() {
    stubFilter(actorFilter(1990, 1999));
    stubCastCredits(HANKS, credit(1L, "Philadelphia", "1993-12-22"));

    service.search("q");

    verify(movies, never()).discover(any(), any(), any(), anyInt());
  }

  // ---------------------------------------------------------------------------------- franchises

  /**
   * "James Bond movies after 2000": the franchise's movies are fetched, and the year filter keeps
   * only the ones released after 2000.
   */
  @Test
  @DisplayName("returns a franchise's movies released inside the year range")
  void franchiseWithYears() {
    stubFilter(filter(null, null, 2000, null, null, "James Bond", List.of()));
    stubBond(
        item(646L, "Dr. No", "1962-10-05"),
        item(36557L, "Casino Royale", "2006-11-14"),
        item(37724L, "Skyfall", "2012-10-25"));

    List<String> titles = titlesOf(service.search("James Bond movies after 2000"));

    assertThat(titles).containsExactly("Casino Royale", "Skyfall");
  }

  /**
   * "Daniel Craig as James Bond": a movie must be in both sets, so only the Bond films Daniel Craig
   * acted in are returned, not his other films and not the other actors' Bond films.
   */
  @Test
  @DisplayName("returns only movies that are in both the person's credits and the franchise")
  void personAndFranchiseAreIntersected() {
    stubFilter(
        filter("Daniel Craig", QueryFilterRole.ACTED, null, null, null, "James Bond", List.of()));
    stubCastCredits(
        CRAIG,
        credit(36557L, "Casino Royale", "2006-11-14"),
        credit(546554L, "Knives Out", "2019-11-27"));
    stubBond(item(646L, "Dr. No", "1962-10-05"), item(36557L, "Casino Royale", "2006-11-14"));

    List<String> titles = titlesOf(service.search("Daniel Craig as James Bond"));

    assertThat(titles).containsExactly("Casino Royale");
  }

  /**
   * TMDB has no collection with that name, so the franchise name is searched as a title instead.
   * Series names often appear in their movies' titles.
   */
  @Test
  @DisplayName("searches an unknown franchise name as a title")
  void unknownFranchiseIsSearchedAsATitle() {
    stubFilter(filter(null, null, null, null, null, "Star Trek", List.of()));
    when(movies.findCollectionId("Star Trek")).thenReturn(Optional.empty());
    when(movies.searchByTitle(any(), anyInt()))
        .thenReturn(List.of(item(1L, "Star Trek: Generations", "1994-11-18")));

    assertThat(titlesOf(service.search("Star Trek"))).containsExactly("Star Trek: Generations");
  }

  /**
   * The model sometimes puts a franchise in {@code personName}. When no person has that name, the
   * name is tried as a franchise, so the query still works.
   */
  @Test
  @DisplayName("tries a name that is not a person as a franchise")
  void unknownPersonIsTriedAsAFranchise() {
    stubFilter(filter("James Bond", null, 2000, null, null, null, List.of()));
    when(actors.findPersonId("James Bond")).thenReturn(Optional.empty());
    stubBond(item(646L, "Dr. No", "1962-10-05"), item(36557L, "Casino Royale", "2006-11-14"));

    assertThat(titlesOf(service.search("James Bond after 2000"))).containsExactly("Casino Royale");
  }

  // ------------------------------------------------------------------- keywords and discovery

  /**
   * With no person or franchise, each keyword is searched by title. The model then checks the
   * results against the query, because a title search returns anything that shares a word.
   */
  @Test
  @DisplayName("searches keywords by title and keeps the movies the model says fit")
  void keywordsAreSearchedByTitleAndChecked() {
    stubFilter(filter(null, null, null, null, null, null, List.of("heist")));
    SearchResultMovieDto heat = movie(1L, "Heat", "1995-12-15");
    when(movies.searchByTitle(any(), anyInt()))
        .thenReturn(
            List.of(item(1L, "Heat", "1995-12-15"), item(2L, "Heist Chicken", "2001-01-01")));
    when(relevance.filter("heist movies", List.of(heat, movie(2L, "Heist Chicken", "2001-01-01"))))
        .thenReturn(List.of(heat));

    assertThat(titlesOf(service.search("heist movies"))).containsExactly("Heat");
  }

  /**
   * "Action movies from 2015 to 2020" names no person, franchise or keyword. The genre name becomes
   * a genre id and movie-service discovers movies for it and the years. The genre is already
   * applied by discover, so the model is not asked about it again.
   */
  @Test
  @DisplayName("discovers movies by years and genre when nothing else is named")
  void yearsAndGenreUseDiscover() {
    stubFilter(filter(null, null, 2015, 2020, "Action", null, List.of()));
    when(movies.findGenreId("Action")).thenReturn(Optional.of(28L));
    when(movies.discover(2015, 2020, 28L, 100))
        .thenReturn(List.of(item(1L, "Mad Max: Fury Road", "2015-05-13")));

    assertThat(titlesOf(service.search("action movies from 2015 to 2020")))
        .containsExactly("Mad Max: Fury Road");
    verify(relevance, never()).filter(anyString(), any());
  }

  // --------------------------------------------------------------------------- the model check

  /** A genre on a person's credits cannot be looked up, so the model checks it. */
  @Test
  @DisplayName("asks the model to check a genre on a person's credits")
  void genreOnAPersonIsCheckedByTheModel() {
    stubFilter(filter("Tom Hanks", QueryFilterRole.ACTED, null, null, "Comedy", null, List.of()));
    stubCastCredits(HANKS, credit(1L, "Big", "1988-06-03"), credit(2L, "Cast Away", "2000-12-22"));
    when(relevance.filter(anyString(), any())).thenReturn(List.of(movie(1L, "Big", "1988-06-03")));

    assertThat(titlesOf(service.search("Tom Hanks comedies"))).containsExactly("Big");
  }

  /** A query the data can check completely never needs the model. */
  @Test
  @DisplayName("does not ask the model when there is no genre and no keyword")
  void noModelCheckWithoutGenreOrKeywords() {
    stubFilter(actorFilter(1990, 1999));
    stubCastCredits(HANKS, credit(1L, "Philadelphia", "1993-12-22"));

    service.search("q");

    verify(relevance, never()).filter(anyString(), any());
  }

  // --------------------------------------------------------------------------------- relaxing

  /**
   * The genre check removes everything, so the genre is dropped and the movies for the rest of the
   * query are returned. The response names the genre, so the user knows.
   */
  @Test
  @DisplayName("drops the genre when it leaves nothing, and says so")
  void relaxesTheGenre() {
    stubFilter(filter("Tom Hanks", QueryFilterRole.ACTED, 1990, 1999, "Comedy", null, List.of()));
    stubCastCredits(HANKS, credit(1L, "Philadelphia", "1993-12-22"));
    when(relevance.filter(anyString(), any())).thenReturn(List.of());

    NaturalLanguageSearchResponseDto response = service.search("Tom Hanks comedies in the 1990s");

    assertThat(titlesOf(response)).containsExactly("Philadelphia");
    assertThat(response.relaxedCriteria()).containsExactly("genre");
  }

  /** Keywords are the weakest criterion, so they are dropped before the genre. */
  @Test
  @DisplayName("drops keywords first, then the genre")
  void relaxesKeywordsBeforeGenre() {
    stubFilter(
        filter("Tom Hanks", QueryFilterRole.ACTED, null, null, "Comedy", null, List.of("space")));
    stubCastCredits(HANKS, credit(1L, "Big", "1988-06-03"));
    when(relevance.filter(anyString(), any())).thenReturn(List.of());

    NaturalLanguageSearchResponseDto response = service.search("Tom Hanks space comedies");

    assertThat(titlesOf(response)).containsExactly("Big");
    assertThat(response.relaxedCriteria()).containsExactly("keywords", "genre");
  }

  /** A query that works as asked reports nothing dropped. */
  @Test
  @DisplayName("reports no dropped criteria when the whole query worked")
  void reportsNothingWhenNothingWasDropped() {
    stubFilter(actorFilter(1990, 1999));
    stubCastCredits(HANKS, credit(1L, "Philadelphia", "1993-12-22"));

    assertThat(service.search("q").relaxedCriteria()).isEmpty();
  }

  /**
   * Years are relaxed last. Here the person has no movie in the range, so with nothing else to drop
   * the range itself goes, and the response says so.
   */
  @Test
  @DisplayName("drops the years last when the person has nothing in that range")
  void relaxesTheYearsLast() {
    stubFilter(actorFilter(1950, 1959));
    stubCastCredits(HANKS, credit(1L, "Philadelphia", "1993-12-22"));

    NaturalLanguageSearchResponseDto response = service.search("Tom Hanks in the 1950s");

    assertThat(titlesOf(response)).containsExactly("Philadelphia");
    assertThat(response.relaxedCriteria()).containsExactly("years");
  }

  /**
   * The person is what the query is about. When no such person exists, dropping other criteria
   * cannot help, so the result is empty and nothing is reported as dropped.
   */
  @Test
  @DisplayName("never drops the person, and reports nothing when nothing is found")
  void neverRelaxesThePerson() {
    stubFilter(filter("Nobody Real", QueryFilterRole.ACTED, 1990, 1999, null, null, List.of()));
    when(actors.findPersonId("Nobody Real")).thenReturn(Optional.empty());
    when(movies.findCollectionId("Nobody Real")).thenReturn(Optional.empty());

    NaturalLanguageSearchResponseDto response = service.search("Nobody Real movies");

    assertThat(response.results()).isEmpty();
    assertThat(response.relaxedCriteria()).isEmpty();
  }

  // ------------------------------------------------------------------ sorting, count, rating

  /** "Sorted by rating" puts the best-rated movie first; a movie with no rating goes last. */
  @Test
  @DisplayName("sorts by rating, best first, unrated last")
  void sortsByRating() {
    stubFilter(sorted(SearchSort.RATING, null, null));
    stubCastCredits(
        HANKS,
        rated(1L, "Middle", "1994-01-01", 7.0),
        rated(2L, "Best", "1995-01-01", 8.8),
        rated(3L, "Unrated", "1996-01-01", null),
        rated(4L, "Worst", "1997-01-01", 5.1));

    assertThat(titlesOf(service.search("q"))).containsExactly("Best", "Middle", "Worst", "Unrated");
  }

  /**
   * "Sorted by revenue" needs the revenue, which credits do not carry, so it is fetched from movie
   * details. Movies with no known revenue go last.
   */
  @Test
  @DisplayName("sorts by revenue using the movie details")
  void sortsByRevenue() {
    stubFilter(sorted(SearchSort.REVENUE, null, null));
    stubCastCredits(
        HANKS,
        credit(1L, "Small", "2001-01-01"),
        credit(2L, "Huge", "2002-01-01"),
        credit(3L, "Unknown", "2003-01-01"),
        credit(4L, "Medium", "2004-01-01"));
    when(movies.fetchMovieDetails(any()))
        .thenReturn(
            List.of(
                details(1L, "Small", 1_000L),
                details(2L, "Huge", 900_000_000L),
                details(3L, "Unknown", 0L),
                details(4L, "Medium", 50_000_000L)));

    NaturalLanguageSearchResponseDto response = service.search("q");

    assertThat(titlesOf(response)).containsExactly("Huge", "Medium", "Small", "Unknown");
    assertThat(response.results().get(0).revenue()).isEqualTo(900_000_000L);
  }

  /** Revenue is only fetched when the query asks for it. */
  @Test
  @DisplayName("does not fetch details when no sort by revenue was asked for")
  void doesNotFetchDetailsWithoutARevenueSort() {
    stubFilter(sorted(SearchSort.RATING, null, null));
    stubCastCredits(HANKS, rated(1L, "One", "2001-01-01", 7.0));

    service.search("q");

    verify(movies, never()).fetchMovieDetails(any());
  }

  /** Release-date sorts read the date text. */
  @Test
  @DisplayName("sorts by release date, newest or oldest first")
  void sortsByReleaseDate() {
    stubCastCredits(
        HANKS,
        credit(1L, "Middle", "1995-05-05"),
        credit(2L, "Newest", "2010-01-01"),
        credit(3L, "Oldest", "1980-12-31"));

    stubFilter(sorted(SearchSort.NEWEST, null, null));
    assertThat(titlesOf(service.search("q"))).containsExactly("Newest", "Middle", "Oldest");

    stubFilter(sorted(SearchSort.OLDEST, null, null));
    assertThat(titlesOf(service.search("q"))).containsExactly("Oldest", "Middle", "Newest");
  }

  /** "Top 2 highest rated": the count is taken after sorting, so it keeps the two best. */
  @Test
  @DisplayName("keeps the first N after sorting")
  void limitIsAppliedAfterSorting() {
    stubFilter(sorted(SearchSort.RATING, 2, null));
    stubCastCredits(
        HANKS,
        rated(1L, "Third", "1994-01-01", 7.0),
        rated(2L, "First", "1995-01-01", 9.0),
        rated(3L, "Second", "1996-01-01", 8.0));

    assertThat(titlesOf(service.search("q"))).containsExactly("First", "Second");
  }

  /** A minimum rating drops movies below it, and movies with no rating. */
  @Test
  @DisplayName("drops movies below the minimum rating")
  void minimumRatingDropsLowRatedMovies() {
    stubFilter(sorted(null, null, 7.0));
    stubCastCredits(
        HANKS,
        rated(1L, "Good", "1994-01-01", 7.0),
        rated(2L, "Bad", "1995-01-01", 6.9),
        rated(3L, "Unrated", "1996-01-01", null));

    assertThat(titlesOf(service.search("q"))).containsExactly("Good");
  }

  /** A threshold nothing reaches is the weakest criterion after keywords, so it is dropped. */
  @Test
  @DisplayName("drops the minimum rating when nothing reaches it, and says so")
  void relaxesTheMinimumRating() {
    stubFilter(sorted(null, null, 9.5));
    stubCastCredits(HANKS, rated(1L, "Good", "1994-01-01", 8.0));

    NaturalLanguageSearchResponseDto response = service.search("q");

    assertThat(titlesOf(response)).containsExactly("Good");
    assertThat(response.relaxedCriteria()).containsExactly("minRating");
  }

  /** "Top 10 highest rated movies" names nothing to look up, so it starts from popular movies. */
  @Test
  @DisplayName("starts from popular movies when a query only sorts and counts")
  void startsFromPopularMoviesWhenNothingIsNamed() {
    stubFilter(
        new StructuredQueryFilterDto(
            null,
            null,
            null,
            null,
            List.of(),
            null,
            List.of(),
            null,
            List.of(),
            SearchSort.RATING,
            2,
            null,
            null,
            null));
    when(movies.discover(null, null, null, 100))
        .thenReturn(
            List.of(
                new MovieListItem(1L, "A", "", "2001-01-01", null, 6.0),
                new MovieListItem(2L, "B", "", "2002-01-01", null, 9.0),
                new MovieListItem(3L, "C", "", "2003-01-01", null, 8.0)));

    assertThat(titlesOf(service.search("top 2 highest rated movies"))).containsExactly("B", "C");
  }

  // ------------------------------------------------------------------------------------ awards

  /** "Movies that won the Oscar for best actor": the winners' details become the results. */
  @Test
  @DisplayName("returns the movies that won an Oscar category")
  void awardWinnersAreReturned() {
    stubFilter(award(OscarCategory.BEST_ACTOR, null, null));
    when(awards.findWinningMovieIds(OscarCategory.BEST_ACTOR)).thenReturn(List.of(872585L, 98L));
    when(movies.fetchMovieDetails(List.of(872585L, 98L)))
        .thenReturn(
            List.of(
                new MovieDetails(872585L, "Oppenheimer", "", "2023-07-19", null, 8.1, 950_000_000L),
                new MovieDetails(98L, "Gladiator", "", "2000-05-01", null, 8.2, 460_000_000L)));

    NaturalLanguageSearchResponseDto response = service.search("movies that won best actor");

    assertThat(titlesOf(response)).containsExactly("Oppenheimer", "Gladiator");
    verify(actors, never()).findPersonId(any());
  }

  /** "Best picture winners from the last 20 years": the award set is cut by the year range. */
  @Test
  @DisplayName("applies the year range to award winners")
  void awardWinnersAreCutByYears() {
    stubFilter(award(OscarCategory.BEST_PICTURE, 2006, null));
    when(awards.findWinningMovieIds(OscarCategory.BEST_PICTURE)).thenReturn(List.of(1L, 2L, 3L));
    when(movies.fetchMovieDetails(any()))
        .thenReturn(
            List.of(
                new MovieDetails(1L, "Old Winner", "", "1994-01-01", null, 8.0, 1L),
                new MovieDetails(2L, "Recent Winner", "", "2016-01-01", null, 8.0, 1L),
                new MovieDetails(3L, "Newest Winner", "", "2024-01-01", null, 8.0, 1L)));

    assertThat(titlesOf(service.search("q"))).containsExactly("Recent Winner", "Newest Winner");
  }

  /**
   * When Wikidata has nothing, dropping other criteria cannot help. The result is empty and nothing
   * is reported as dropped.
   */
  @Test
  @DisplayName("returns nothing, and drops nothing, when there are no award winners")
  void noAwardWinnersGivesNothing() {
    stubFilter(award(OscarCategory.BEST_ACTOR, 2000, null));
    when(awards.findWinningMovieIds(OscarCategory.BEST_ACTOR)).thenReturn(List.of());

    NaturalLanguageSearchResponseDto response = service.search("q");

    assertThat(response.results()).isEmpty();
    assertThat(response.relaxedCriteria()).isEmpty();
  }

  // ------------------------------------------------------------------------------- plain title

  /** A query that is just a title is searched as a title, as before. */
  @Test
  @DisplayName("searches a plain title as a title")
  void plainTitleIsSearchedAsATitle() {
    stubFilter(
        new StructuredQueryFilterDto(
            null, null, null, null, List.of(), null, List.of(), "Inception"));
    when(movies.searchByTitle("Inception", 200))
        .thenReturn(List.of(item(27205L, "Inception", "2010-07-15")));

    assertThat(titlesOf(service.search("Inception"))).containsExactly("Inception");
    verify(actors, never()).findPersonId(any());
  }

  // ---------------------------------------------------------------------------------- helpers

  /**
   * Builds a filter for an actor named Tom Hanks with the given years.
   *
   * @param yearFrom first year, or null
   * @param yearTo last year, or null
   * @return the filter the parser would return
   */
  private static StructuredQueryFilterDto actorFilter(Integer yearFrom, Integer yearTo) {
    return filter("Tom Hanks", QueryFilterRole.ACTED, yearFrom, yearTo, null, null, List.of());
  }

  /**
   * Builds a filter from the fields a test cares about.
   *
   * @param person person name, or null
   * @param role role, or null
   * @param yearFrom first year, or null
   * @param yearTo last year, or null
   * @param genre genre, or null
   * @param franchise franchise, or null
   * @param keywords keywords
   * @return the filter
   */
  private static StructuredQueryFilterDto filter(
      String person,
      QueryFilterRole role,
      Integer yearFrom,
      Integer yearTo,
      String genre,
      String franchise,
      List<String> keywords) {
    return new StructuredQueryFilterDto(
        person, role, yearFrom, yearTo, List.of(), genre, List.of(), franchise, keywords, null);
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
   * Makes actor-service return these cast credits for a person.
   *
   * @param personId the person's id
   * @param credits the person's cast credits
   */
  private void stubCastCredits(long personId, PersonCredit... credits) {
    when(actors.fetchCastCredits(personId)).thenReturn(List.of(credits));
  }

  /**
   * Makes movie-service find the James Bond collection with these movies.
   *
   * @param bondMovies the movies in the collection
   */
  private void stubBond(MovieListItem... bondMovies) {
    when(movies.findCollectionId("James Bond")).thenReturn(Optional.of(BOND_COLLECTION));
    when(movies.fetchCollectionMovies(BOND_COLLECTION)).thenReturn(List.of(bondMovies));
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
   * Builds one movie as movie-service lists it.
   *
   * @param id movie id
   * @param title movie title
   * @param releaseDate release date as {@code yyyy-MM-dd}
   * @return the list item
   */
  private static MovieListItem item(long id, String title, String releaseDate) {
    return new MovieListItem(id, title, "", releaseDate, null, 7.0);
  }

  /**
   * Builds the result the service makes from {@link #item}.
   *
   * @param id movie id
   * @param title movie title
   * @param releaseDate release date as {@code yyyy-MM-dd}
   * @return the result
   */
  private static SearchResultMovieDto movie(long id, String title, String releaseDate) {
    return new SearchResultMovieDto(id, title, "", releaseDate, null, 7.0);
  }

  /**
   * Builds a filter for Tom Hanks with a sort, a count and a minimum rating.
   *
   * @param sort the order, or null
   * @param limit the count, or null
   * @param minRating the minimum rating, or null
   * @return the filter
   */
  private static StructuredQueryFilterDto sorted(SearchSort sort, Integer limit, Double minRating) {
    return new StructuredQueryFilterDto(
        "Tom Hanks",
        QueryFilterRole.ACTED,
        null,
        null,
        List.of(),
        null,
        List.of(),
        null,
        List.of(),
        sort,
        limit,
        minRating,
        null,
        null);
  }

  /**
   * Builds a filter that asks only for the winners of an Oscar category.
   *
   * @param category the category
   * @param yearFrom first year, or null
   * @param yearTo last year, or null
   * @return the filter
   */
  private static StructuredQueryFilterDto award(
      OscarCategory category, Integer yearFrom, Integer yearTo) {
    return new StructuredQueryFilterDto(
        null, null, yearFrom, yearTo, List.of(), null, List.of(), null, List.of(), null, null, null,
        category, null);
  }

  /**
   * Builds a cast credit with a rating.
   *
   * @param id movie id
   * @param title movie title
   * @param releaseDate release date
   * @param rating average vote, or null
   * @return the credit
   */
  private static PersonCredit rated(long id, String title, String releaseDate, Double rating) {
    return new PersonCredit(id, title, releaseDate, null, rating);
  }

  /**
   * Builds movie details with a revenue.
   *
   * @param id movie id
   * @param title movie title
   * @param revenue revenue in dollars
   * @return the details
   */
  private static MovieDetails details(long id, String title, long revenue) {
    return new MovieDetails(id, title, "", "2000-01-01", null, 7.0, revenue);
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
