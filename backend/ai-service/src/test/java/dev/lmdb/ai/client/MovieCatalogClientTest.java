package dev.lmdb.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests the franchise, genre and discover calls of {@link MovieCatalogClient}. A {@link
 * MockRestServiceServer} stands in for movie-service, so each test checks the request the client
 * sends and how it reads the reply.
 */
@DisplayName("MovieCatalogClient (franchise, genre and discover calls)")
class MovieCatalogClientTest {

  private static final String BASE = "http://movie-service";

  private MockRestServiceServer server;
  private MovieCatalogClient client;

  /** Builds a client whose HTTP calls go to the mock server. */
  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new MovieCatalogClient(builder.build());
  }

  /** The first collection movie-service lists is the best match, so its id is used. */
  @Test
  @DisplayName("findCollectionId returns the id of the first matching collection")
  void findsTheFirstMatchingCollection() {
    server
        .expect(requestTo(startsWith(BASE + "/api/v1/movies/collections/search")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("query", "James%20Bond"))
        .andRespond(
            withSuccess(
                """
                [{"id":645,"name":"James Bond Collection","posterPath":"/b.jpg"},
                 {"id":99,"name":"James Bond Jr."}]
                """,
                MediaType.APPLICATION_JSON));

    Optional<Long> id = client.findCollectionId("James Bond");

    assertThat(id).contains(645L);
    server.verify();
  }

  /** No collection with that name is a normal answer, not an error. */
  @Test
  @DisplayName("findCollectionId is empty when no collection matches")
  void noMatchingCollectionGivesEmpty() {
    server
        .expect(requestTo(startsWith(BASE + "/api/v1/movies/collections/search")))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThat(client.findCollectionId("zzz")).isEmpty();
  }

  /** A failing movie-service must not fail the whole search: the lookup just finds nothing. */
  @Test
  @DisplayName("findCollectionId is empty when movie-service fails")
  void collectionLookupSurvivesAServerError() {
    server
        .expect(requestTo(startsWith(BASE + "/api/v1/movies/collections/search")))
        .andRespond(withServerError());

    assertThat(client.findCollectionId("James Bond")).isEmpty();
  }

  /** The movies of a collection come back with their release dates, which the year filter needs. */
  @Test
  @DisplayName("fetchCollectionMovies reads the movies and their release dates")
  void readsTheMoviesOfACollection() {
    server
        .expect(requestTo(BASE + "/api/v1/movies/collections/645"))
        .andRespond(
            withSuccess(
                """
                {"id":645,"name":"James Bond Collection","movies":[
                  {"tmdbId":36557,"title":"Casino Royale","releaseDate":"2006-11-14"},
                  {"tmdbId":10764,"title":"Quantum of Solace","releaseDate":"2008-10-30"}]}
                """,
                MediaType.APPLICATION_JSON));

    List<MovieListItem> movies = client.fetchCollectionMovies(645L);

    assertThat(movies)
        .extracting(MovieListItem::title)
        .containsExactly("Casino Royale", "Quantum of Solace");
    assertThat(movies.get(0).releaseDate()).isEqualTo("2006-11-14");
  }

  /** A failing movie-service gives no movies rather than an exception. */
  @Test
  @DisplayName("fetchCollectionMovies is empty when movie-service fails")
  void collectionFetchSurvivesAServerError() {
    server.expect(requestTo(BASE + "/api/v1/movies/collections/645")).andRespond(withServerError());

    assertThat(client.fetchCollectionMovies(645L)).isEmpty();
  }

  /** Genre names in a query are matched to movie-service's genre ids without regard to case. */
  @Test
  @DisplayName("findGenreId matches a genre name without regard to case")
  void findsAGenreIdByName() {
    server
        .expect(requestTo(BASE + "/api/v1/genres"))
        .andRespond(
            withSuccess(
                """
                [{"id":28,"name":"Action"},{"id":35,"name":"Comedy"}]
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.findGenreId("comedy")).contains(35L);
  }

  /** An unknown genre is not an error; the caller then searches without a genre. */
  @Test
  @DisplayName("findGenreId is empty for an unknown genre")
  void unknownGenreGivesEmpty() {
    server
        .expect(requestTo(BASE + "/api/v1/genres"))
        .andRespond(withSuccess("[{\"id\":28,\"name\":\"Action\"}]", MediaType.APPLICATION_JSON));

    assertThat(client.findGenreId("Space Western")).isEmpty();
  }

  /** Discover sends the constraints it was given, and returns the movies with their dates. */
  @Test
  @DisplayName("discover sends the year range, genre and size")
  void discoverSendsTheConstraints() {
    server
        .expect(requestTo(startsWith(BASE + "/api/v1/movies/discover")))
        .andExpect(queryParam("yearFrom", "2015"))
        .andExpect(queryParam("yearTo", "2020"))
        .andExpect(queryParam("genreId", "28"))
        .andExpect(queryParam("size", "50"))
        .andRespond(
            withSuccess(
                """
                {"content":[{"tmdbId":1,"title":"Mad Max: Fury Road","releaseDate":"2015-05-13"}],
                 "pageNumber":0,"pageSize":50,"totalElements":1,"totalPages":1,
                 "first":true,"last":true,"hasNext":false,"hasPrevious":false,"numberOfElements":1}
                """,
                MediaType.APPLICATION_JSON));

    List<MovieListItem> movies = client.discover(2015, 2020, 28L, 50);

    assertThat(movies).extracting(MovieListItem::title).containsExactly("Mad Max: Fury Road");
    server.verify();
  }
}
