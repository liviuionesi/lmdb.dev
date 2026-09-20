package dev.lmdb.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Twenty queries a user could type, run through the whole search: reading the query, looking things
 * up, combining, filtering, sorting and counting. The model and the three data sources are fakes,
 * so each scenario says exactly what the model replied and what the catalog holds, and the test
 * checks the movies and their order.
 *
 * <p>Each model reply is what a 7B model returns for that query, mistakes included. The clock is
 * fixed at 2026-09-20, so "the last 20 years" starts in 2006 on every day the test runs.
 *
 * <p>The same 20 queries run against the real stack, model and TMDB in {@code
 * infrastructure/scripts/test-semantic-search.sh}.
 */
@DisplayName("Semantic search: 20 useful queries")
class SemanticSearchScenariosTest {

  private static final Clock TODAY =
      Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneOffset.UTC);

  private ActorCatalogClient actors;
  private MovieCatalogClient movies;
  private AwardsClient awards;
  private ChatModel parseModel;
  private ChatModel relevanceModel;
  private QueryAggregationService service;
  private final Map<Long, MovieDetails> detailsById = new HashMap<>();

  /** What one scenario sets up, asks, and expects. */
  private record Scenario(
      String query,
      String modelReply,
      String relevanceReply,
      Consumer<SemanticSearchScenariosTest> catalog,
      List<String> expectedTitles,
      List<String> expectedRelaxed) {

    @Override
    public String toString() {
      return query;
    }
  }

  /** Builds the search on fakes: one for the parsing model, one for the relevance check. */
  @BeforeEach
  void setUp() {
    actors = mock(ActorCatalogClient.class);
    movies = mock(MovieCatalogClient.class);
    awards = mock(AwardsClient.class);
    parseModel = mock(ChatModel.class);
    relevanceModel = mock(ChatModel.class);
    when(parseModel.getOptions()).thenReturn(ChatOptions.builder().build());
    when(relevanceModel.getOptions()).thenReturn(ChatOptions.builder().build());
    service =
        new QueryAggregationService(
            new QueryParsingService(ChatClient.builder(parseModel), TODAY),
            actors,
            movies,
            new RelevanceFilterService(ChatClient.builder(relevanceModel)),
            awards);
    when(movies.fetchMovieDetails(any()))
        .thenAnswer(
            call -> {
              List<MovieDetails> found = new ArrayList<>();
              for (Object id : (java.util.Collection<?>) call.getArgument(0)) {
                MovieDetails details = detailsById.get((Long) id);
                if (details != null) {
                  found.add(details);
                }
              }
              return found;
            });
  }

  /**
   * Runs one scenario and checks the movies, their order, and the criteria that were dropped.
   *
   * @param scenario the query, the fakes' data and the expected result
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("scenarios")
  @DisplayName("returns the expected movies in the expected order")
  void returnsTheExpectedMovies(Scenario scenario) {
    reply(parseModel, scenario.modelReply());
    if (scenario.relevanceReply() != null) {
      reply(relevanceModel, scenario.relevanceReply());
    }
    scenario.catalog().accept(this);

    NaturalLanguageSearchResponseDto response = service.search(scenario.query());

    assertThat(response.results().stream().map(SearchResultMovieDto::title).toList())
        .containsExactlyElementsOf(scenario.expectedTitles());
    assertThat(response.relaxedCriteria()).containsExactlyElementsOf(scenario.expectedRelaxed());
  }

  // ------------------------------------------------------------------------------- the 20

  /**
   * The twenty scenarios.
   *
   * @return one argument per scenario
   */
  static Stream<Arguments> scenarios() {
    return Stream.of(
            // --- People, time and order -------------------------------------------------------
            // 1. The user's own example: relative years and a sort by rating.
            scenario(
                "Tom Cruise movies from the last 20 years sorted by rating",
                person("Tom Cruise", "ACTED"),
                null,
                w -> {
                  w.person("Tom Cruise", 500);
                  w.cast(
                      500,
                      c(1, "Top Gun: Maverick", "2022-05-24", 8.2),
                      c(2, "Mission: Impossible - Fallout", "2018-07-25", 7.4),
                      c(3, "Edge of Tomorrow", "2014-05-27", 7.8),
                      c(4, "Jack Reacher", "2012-12-20", 6.6),
                      c(5, "Knight and Day", "2010-06-23", 6.7),
                      c(6, "Collateral", "2004-08-04", 7.4),
                      c(7, "Rain Man", "1988-12-12", 7.7));
                },
                List.of(
                    "Top Gun: Maverick",
                    "Edge of Tomorrow",
                    "Mission: Impossible - Fallout",
                    "Knight and Day",
                    "Jack Reacher")),
            // 2. The same, sorted by revenue. Revenue is not in credits, so it comes from details.
            scenario(
                "Tom Cruise movies from the last 20 years sorted by revenue",
                person("Tom Cruise", "ACTED"),
                null,
                w -> {
                  w.person("Tom Cruise", 500);
                  w.cast(
                      500,
                      c(1, "Top Gun: Maverick", "2022-05-24", 8.2),
                      c(2, "Mission: Impossible - Fallout", "2018-07-25", 7.4),
                      c(3, "Edge of Tomorrow", "2014-05-27", 7.8),
                      c(4, "Jack Reacher", "2012-12-20", 6.6),
                      c(6, "Collateral", "2004-08-04", 7.4));
                  w.details(
                      d(1, "Top Gun: Maverick", "2022-05-24", 8.2, 1_495_000_000L),
                      d(2, "Mission: Impossible - Fallout", "2018-07-25", 7.4, 791_000_000L),
                      d(3, "Edge of Tomorrow", "2014-05-27", 7.8, 370_000_000L),
                      d(4, "Jack Reacher", "2012-12-20", 6.6, 218_000_000L));
                },
                List.of(
                    "Top Gun: Maverick",
                    "Mission: Impossible - Fallout",
                    "Edge of Tomorrow",
                    "Jack Reacher")),
            // 3. A decade.
            scenario(
                "Leonardo DiCaprio movies in the 2010s",
                person("Leonardo DiCaprio", "ACTED"),
                null,
                w -> {
                  w.person("Leonardo DiCaprio", 6193);
                  w.cast(
                      6193,
                      c(1, "Inception", "2010-07-15", 8.4),
                      c(2, "The Wolf of Wall Street", "2013-12-25", 8.0),
                      c(3, "The Revenant", "2015-12-25", 7.5),
                      c(4, "Titanic", "1997-11-18", 7.9),
                      c(5, "The Departed", "2006-10-05", 8.2),
                      c(6, "Once Upon a Time in Hollywood", "2019-07-24", 7.6));
                },
                List.of(
                    "Inception",
                    "The Wolf of Wall Street",
                    "The Revenant",
                    "Once Upon a Time in Hollywood")),
            // 4. A keyword next to a person: the model check keeps the heist movies.
            scenario(
                "heist movies with Brad Pitt",
                "{\"personName\":\"Brad Pitt\",\"role\":\"ACTED\",\"keywords\":[\"heist\"]}",
                "{\"matches\":[1,3]}",
                w -> {
                  w.person("Brad Pitt", 287);
                  w.cast(
                      287,
                      c(1, "Ocean's Eleven", "2001-12-07", 7.4),
                      c(2, "Fight Club", "1999-10-15", 8.4),
                      c(3, "Ocean's Twelve", "2004-12-10", 6.5));
                },
                List.of("Ocean's Eleven", "Ocean's Twelve")),
            // --- Directors, producers, negation and rating ------------------------------------
            // 5. A role and a sort.
            scenario(
                "movies directed by Christopher Nolan sorted by rating",
                person("Christopher Nolan", "DIRECTED"),
                null,
                w -> {
                  w.person("Christopher Nolan", 525);
                  w.crew(
                      525,
                      QueryFilterRole.DIRECTED,
                      c(1, "Tenet", "2020-08-22", 7.2),
                      c(2, "The Dark Knight", "2008-07-16", 8.5),
                      c(3, "Inception", "2010-07-15", 8.4),
                      c(4, "Interstellar", "2014-11-05", 8.4),
                      c(5, "Dunkirk", "2017-07-19", 7.5));
                },
                List.of("The Dark Knight", "Inception", "Interstellar", "Dunkirk", "Tenet")),
            // 6. A count after a sort.
            scenario(
                "top 3 highest rated movies directed by Quentin Tarantino",
                person("Quentin Tarantino", "DIRECTED"),
                null,
                w -> {
                  w.person("Quentin Tarantino", 138);
                  w.crew(
                      138,
                      QueryFilterRole.DIRECTED,
                      c(1, "Pulp Fiction", "1994-09-10", 8.5),
                      c(2, "Django Unchained", "2012-12-25", 8.2),
                      c(3, "Jackie Brown", "1997-12-25", 7.4),
                      c(4, "Inglourious Basterds", "2009-08-19", 8.2),
                      c(5, "The Hateful Eight", "2015-12-25", 7.7));
                },
                List.of("Pulp Fiction", "Django Unchained", "Inglourious Basterds")),
            // 7. The producer role and an open-ended range.
            scenario(
                "movies produced by Steven Spielberg after 2000",
                person("Steven Spielberg", "PRODUCED", 2000, null),
                null,
                w -> {
                  w.person("Steven Spielberg", 488);
                  w.crew(
                      488,
                      QueryFilterRole.PRODUCED,
                      c(1, "Transformers", "2007-06-27", 6.9),
                      c(2, "Jurassic Park", "1993-06-11", 8.0),
                      c(3, "Lincoln", "2012-11-09", 7.3));
                },
                List.of("Transformers", "Lincoln")),
            // 8. A collaborator: the movie must have both.
            scenario(
                "movies with Brad Pitt and Edward Norton",
                "{\"personName\":\"Brad Pitt\",\"role\":\"ACTED\","
                    + "\"collaborators\":[\"Edward Norton\"]}",
                null,
                w -> {
                  w.person("Brad Pitt", 287);
                  w.person("Edward Norton", 819);
                  w.cast(
                      287, c(1, "Fight Club", "1999-10-15", 8.4), c(2, "Se7en", "1995-09-22", 8.4));
                  w.cast(
                      819,
                      c(1, "Fight Club", "1999-10-15", 8.4),
                      c(3, "Primal Fear", "1996-04-03", 7.7));
                },
                List.of("Fight Club")),
            // 9. Negation: his acting credits minus the ones he directed.
            scenario(
                "films Quentin Tarantino didn't direct",
                "{\"personName\":\"Quentin Tarantino\",\"role\":\"DIRECTED\","
                    + "\"negated\":[\"role\"]}",
                null,
                w -> {
                  w.person("Quentin Tarantino", 138);
                  w.cast(
                      138,
                      c(1, "From Dusk Till Dawn", "1996-01-19", 7.2),
                      c(2, "Desperado", "1995-08-25", 7.0),
                      c(3, "Pulp Fiction", "1994-09-10", 8.5));
                  w.crew(138, QueryFilterRole.DIRECTED, c(3, "Pulp Fiction", "1994-09-10", 8.5));
                },
                List.of("From Dusk Till Dawn", "Desperado")),
            // 10. A minimum rating.
            scenario(
                "Meryl Streep movies rated above 7",
                person("Meryl Streep", "ACTED"),
                null,
                w -> {
                  w.person("Meryl Streep", 5064);
                  w.cast(
                      5064,
                      c(1, "Sophie's Choice", "1982-12-10", 7.8),
                      c(2, "The Devil Wears Prada", "2006-06-30", 7.3),
                      c(3, "Mamma Mia!", "2008-07-18", 6.9),
                      c(4, "Doubt", "2008-12-12", 6.8));
                },
                List.of("Sophie's Choice", "The Devil Wears Prada")),
            // --- Franchises ---------------------------------------------------------------------
            // 11. The user's first query, in short.
            scenario(
                "James Bond movies after 2000",
                "{\"yearFrom\":2000,\"franchise\":\"James Bond\"}",
                null,
                w -> w.bond(),
                List.of("Casino Royale", "Skyfall", "No Time to Die")),
            // 12. A franchise in release order.
            scenario(
                "Star Wars movies sorted by release date newest first",
                "{\"franchise\":\"Star Wars\"}",
                null,
                w -> {
                  w.collection(
                      "Star Wars",
                      10,
                      m(1, "Star Wars", "1977-05-25", 8.2),
                      m(2, "The Empire Strikes Back", "1980-05-21", 8.4),
                      m(3, "The Force Awakens", "2015-12-15", 7.2));
                },
                List.of("The Force Awakens", "The Empire Strikes Back", "Star Wars")),
            // 13. A franchise by money.
            scenario(
                "Harry Potter movies sorted by revenue",
                "{\"franchise\":\"Harry Potter\"}",
                null,
                w -> {
                  w.collection(
                      "Harry Potter",
                      1241,
                      m(1, "Philosopher's Stone", "2001-11-16", 7.9),
                      m(2, "Deathly Hallows: Part 2", "2011-07-13", 8.1),
                      m(3, "Chamber of Secrets", "2002-11-13", 7.7));
                  w.details(
                      d(1, "Philosopher's Stone", "2001-11-16", 7.9, 1_010_000_000L),
                      d(2, "Deathly Hallows: Part 2", "2011-07-13", 8.1, 1_342_000_000L),
                      d(3, "Chamber of Secrets", "2002-11-13", 7.7, 879_000_000L));
                },
                List.of("Deathly Hallows: Part 2", "Philosopher's Stone", "Chamber of Secrets")),
            // 14. A person and a franchise together: only their Bond films.
            scenario(
                "Daniel Craig as James Bond",
                "{\"personName\":\"Daniel Craig\",\"role\":\"ACTED\","
                    + "\"collaborators\":[\"James Bond\"],\"franchise\":\"James Bond\"}",
                null,
                w -> {
                  w.bond();
                  w.person("Daniel Craig", 8784);
                  w.cast(
                      8784,
                      c(36557, "Casino Royale", "2006-11-14", 7.5),
                      c(37724, "Skyfall", "2012-10-25", 7.8),
                      c(546554, "Knives Out", "2019-11-27", 7.8));
                },
                List.of("Casino Royale", "Skyfall")),
            // 15. A franchise whose name has a colon, sorted by rating.
            scenario(
                "Mission: Impossible movies sorted by rating",
                "{\"franchise\":\"Mission: Impossible\"}",
                null,
                w ->
                    w.collection(
                        "Mission: Impossible",
                        87359,
                        m(1, "Mission: Impossible", "1996-05-22", 7.0),
                        m(2, "Ghost Protocol", "2011-12-07", 7.4),
                        m(3, "Fallout", "2018-07-25", 7.4),
                        m(4, "Mission: Impossible II", "2000-05-24", 6.1)),
                List.of(
                    "Ghost Protocol", "Fallout", "Mission: Impossible", "Mission: Impossible II")),
            // --- Genre, years and count, with no name at all ------------------------------------
            // 16. "Best" means sort by rating.
            scenario(
                "best action movies from 2015 to 2020",
                "{\"yearFrom\":2015,\"yearTo\":2020,\"genre\":\"Action\"}",
                null,
                w -> {
                  w.genre("Action", 28);
                  w.discover(
                      2015,
                      2020,
                      28L,
                      m(1, "Mad Max: Fury Road", "2015-05-13", 7.6),
                      m(2, "Avengers: Endgame", "2019-04-24", 8.3),
                      m(3, "John Wick", "2014-10-22", 7.4));
                },
                List.of("Avengers: Endgame", "Mad Max: Fury Road")),
            // 17. A decade, a genre, a sort and a count together.
            scenario(
                "top 2 highest rated comedies of the 1990s",
                "{\"yearFrom\":1990,\"yearTo\":1999,\"genre\":\"Comedy\"}",
                null,
                w -> {
                  w.genre("Comedy", 35);
                  w.discover(
                      1990,
                      1999,
                      35L,
                      m(1, "Groundhog Day", "1993-02-12", 7.6),
                      m(2, "The Big Lebowski", "1998-03-06", 7.9),
                      m(3, "Dumb and Dumber", "1994-12-16", 7.3));
                },
                List.of("The Big Lebowski", "Groundhog Day")),
            // 18. Relative years, a genre and a sort.
            scenario(
                "horror movies from the last 5 years sorted by rating",
                "{\"genre\":\"Horror\"}",
                null,
                w -> {
                  w.genre("Horror", 27);
                  w.discover(
                      2021,
                      null,
                      27L,
                      m(1, "Barbarian", "2022-09-08", 7.0),
                      m(2, "Sinners", "2025-04-16", 7.6),
                      m(3, "Smile", "2022-09-23", 6.8));
                },
                List.of("Sinners", "Barbarian", "Smile")),
            // --- Awards ------------------------------------------------------------------------
            // 19. The user's award example.
            scenario(
                "all the movies that won the oscar for the best actor",
                "{\"personName\":\"Best Actor\",\"keywords\":[\"oscar\"]}",
                null,
                w ->
                    w.winners(
                        OscarCategory.BEST_ACTOR,
                        d(1, "Oppenheimer", "2023-07-19", 8.1, 950_000_000L),
                        d(2, "Gladiator", "2000-05-01", 8.2, 460_000_000L),
                        d(3, "The Whale", "2022-12-09", 7.7, 57_000_000L)),
                List.of("Oppenheimer", "Gladiator", "The Whale")),
            // 20. An award cut by relative years.
            scenario(
                "Oscar best picture winners from the last 20 years",
                "{\"keywords\":[\"oscar\"]}",
                null,
                w ->
                    w.winners(
                        OscarCategory.BEST_PICTURE,
                        d(1, "Titanic", "1997-11-18", 7.9, 2_264_000_000L),
                        d(2, "The Departed", "2006-10-05", 8.2, 291_000_000L),
                        d(3, "Parasite", "2019-05-30", 8.5, 262_000_000L),
                        d(4, "Oppenheimer", "2023-07-19", 8.1, 950_000_000L)),
                List.of("The Departed", "Parasite", "Oppenheimer")))
        .map(Arguments::of);
  }

  // ------------------------------------------------------------------------------- scenario DSL

  private static Scenario scenario(
      String query,
      String modelReply,
      String relevanceReply,
      Consumer<SemanticSearchScenariosTest> catalog,
      List<String> expectedTitles) {
    return new Scenario(query, modelReply, relevanceReply, catalog, expectedTitles, List.of());
  }

  private static String person(String name, String role) {
    return "{\"personName\":\"" + name + "\",\"role\":\"" + role + "\"}";
  }

  private static String person(String name, String role, Integer yearFrom, Integer yearTo) {
    return "{\"personName\":\""
        + name
        + "\",\"role\":\""
        + role
        + "\",\"yearFrom\":"
        + yearFrom
        + ",\"yearTo\":"
        + yearTo
        + "}";
  }

  /** Makes a fake model return the same reply to every call. */
  private static void reply(ChatModel model, String text) {
    when(model.call(any(Prompt.class)))
        .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
  }

  private void person(String name, long id) {
    when(actors.findPersonId(name)).thenReturn(Optional.of(id));
  }

  private void cast(long personId, PersonCredit... credits) {
    when(actors.fetchCastCredits(personId)).thenReturn(List.of(credits));
  }

  private void crew(long personId, QueryFilterRole role, PersonCredit... credits) {
    when(actors.fetchCrewCredits(personId, role)).thenReturn(List.of(credits));
  }

  private void collection(String name, long id, MovieListItem... items) {
    when(movies.findCollectionId(name)).thenReturn(Optional.of(id));
    when(movies.fetchCollectionMovies(id)).thenReturn(List.of(items));
  }

  private void genre(String name, long id) {
    when(movies.findGenreId(name)).thenReturn(Optional.of(id));
  }

  private void discover(Integer from, Integer to, Long genreId, MovieListItem... items) {
    when(movies.discover(from, to, genreId, 100)).thenReturn(List.of(items));
  }

  private void details(MovieDetails... all) {
    for (MovieDetails one : all) {
      detailsById.put(one.tmdbId(), one);
    }
  }

  private void winners(OscarCategory category, MovieDetails... all) {
    details(all);
    when(awards.findWinningMovieIds(category))
        .thenReturn(java.util.Arrays.stream(all).map(MovieDetails::tmdbId).toList());
  }

  /** The James Bond collection, as TMDB lists it. */
  private void bond() {
    collection(
        "James Bond",
        645,
        m(646, "Dr. No", "1962-10-05", 6.9),
        m(36557, "Casino Royale", "2006-11-14", 7.5),
        m(37724, "Skyfall", "2012-10-25", 7.8),
        m(370172, "No Time to Die", "2021-09-28", 7.3));
  }

  private static PersonCredit c(long id, String title, String date, double rating) {
    return new PersonCredit(id, title, date, null, rating);
  }

  private static MovieListItem m(long id, String title, String date, double rating) {
    return new MovieListItem(id, title, "", date, null, rating);
  }

  private static MovieDetails d(long id, String title, String date, double rating, long revenue) {
    return new MovieDetails(id, title, "", date, null, rating, revenue);
  }
}
