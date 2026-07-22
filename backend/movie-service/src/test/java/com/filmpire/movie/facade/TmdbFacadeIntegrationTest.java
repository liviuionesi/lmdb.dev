package com.filmpire.movie.facade;

import com.filmpire.movie.repository.MovieRepository;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the TMDB v3 facade: full controller → service →
 * MongoDB → (WireMock-simulated) TMDB stack.
 *
 * <p>These tests prove the ADR-010 facade contract: responses use TMDB's
 * exact field names/envelope (movie/actor pages don't need any frontend
 * changes beyond the base URL), but the data behind them is genuinely
 * persisted and mapped, not a replayed raw copy — so unlike the pre-ADR-010
 * facade, assertions here check the TMDB-shaped fields the React app
 * actually reads rather than full byte-for-byte body equality, and one test
 * asserts directly against MongoDB to prove the catalog is real.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@WireMockTest(httpPort = 9990)
@DisplayName("TMDB Facade Integration Tests")
class TmdbFacadeIntegrationTest {

    /** Real MongoDB via Testcontainers; @ServiceConnection wires the URI. */
    @Container
    @ServiceConnection
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:8.0.0");

    /** A single TMDB popular-list page fixture reused by several tests. */
    private static final String POPULAR_PAGE_1 = """
        {"page":1,"results":[{"adult":false,"backdrop_path":"/x.jpg","genre_ids":[28,12],\
        "id":550,"original_language":"en","original_title":"Fight Club",\
        "overview":"An insomniac...","popularity":61.4,"poster_path":"/p.jpg",\
        "release_date":"1999-10-15","title":"Fight Club",\
        "vote_average":8.4,"vote_count":26280}],"total_pages":500,"total_results":10000}""";

    private static final String MOVIE_550_DETAILS = """
        {"id":550,"title":"Fight Club","original_title":"Fight Club",\
        "overview":"An insomniac...","poster_path":"/p.jpg","backdrop_path":"/x.jpg",\
        "release_date":"1999-10-15","vote_average":8.4,"vote_count":26280,\
        "genres":[{"id":18,"name":"Drama"}],"runtime":139,"status":"Released",\
        "budget":63000000,"revenue":100853753,"original_language":"en",\
        "popularity":61.4,"adult":false,"imdb_id":"tt0137523"}""";

    private static final String MOVIE_550_VIDEOS = """
        {"id":550,"results":[{"id":"v1","key":"abc123","name":"Trailer",\
        "site":"YouTube","size":1080,"type":"Trailer","official":true,\
        "published_at":"1999-09-01T00:00:00.000Z"}]}""";

    private static final String MOVIE_550_CREDITS = """
        {"id":550,"cast":[{"id":819,"name":"Edward Norton","character":"The Narrator",\
        "profile_path":"/e.jpg","order":0}],"crew":[]}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovieRepository movieRepository;

    /**
     * Points the TMDB base URL at WireMock and fixes the server API key so
     * requests can assert on it.
     *
     * @param registry Spring test property registry
     */
    @DynamicPropertySource
    static void tmdbProperties(DynamicPropertyRegistry registry) {
        registry.add("tmdb.api.base-url", () -> "http://localhost:9990");
        registry.add("tmdb.api.key", () -> "server-side-key");
    }

    /**
     * Starts every test from a clean slate: no persisted movies and no
     * recorded WireMock requests (stubs are re-declared per test).
     */
    @BeforeEach
    void cleanSlate() {
        movieRepository.deleteAll();
        resetAllRequests();
    }

    /**
     * The facade's two load-bearing promises for list endpoints: TMDB's own
     * field names/shape come back (no camelCase, no envelope changes) and
     * the client-sent {@code api_key} is discarded in favor of the server
     * key — asserted by checking WireMock saw only the server key.
     */
    @Test
    @DisplayName("Popular list is TMDB-shaped and the client api_key is swapped for the server key")
    void popularListIsTmdbShapedAndKeyIsSwapped() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/movie/popular"))
            .withQueryParam("api_key", equalTo("server-side-key"))
            .willReturn(okJson(POPULAR_PAGE_1)));

        mockMvc.perform(get("/movie/popular")
                .queryParam("page", "1")
                .queryParam("api_key", "react-app-client-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.total_pages").value(500))
            .andExpect(jsonPath("$.total_results").value(10000))
            .andExpect(jsonPath("$.results[0].id").value(550))
            .andExpect(jsonPath("$.results[0].poster_path").value("/p.jpg"))
            .andExpect(jsonPath("$.results[0].vote_average").value(8.4))
            .andExpect(jsonPath("$.results[0].genre_ids[0]").value(28));

        verify(1, getRequestedFor(urlPathEqualTo("/movie/popular"))
            .withQueryParam("api_key", equalTo("server-side-key")));
        verify(0, getRequestedFor(urlPathEqualTo("/movie/popular"))
            .withQueryParam("api_key", equalTo("react-app-client-key")));
    }

    /**
     * Proves ADR-010's central claim: a list response's movies are genuinely
     * persisted, not just cached bytes. Querying MongoDB directly (bypassing
     * the API entirely) must find the same movie the list response reported.
     */
    @Test
    @DisplayName("Every movie in a list response is upserted into MongoDB")
    void listResultsAreUpsertedIntoMongo() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/movie/popular"))
            .willReturn(okJson(POPULAR_PAGE_1)));

        mockMvc.perform(get("/movie/popular").queryParam("page", "1"))
            .andExpect(status().isOk());

        assertThat(movieRepository.findByTmdbId(550L))
            .isPresent()
            .get()
            .satisfies(movie -> {
                assertThat(movie.getTitle()).isEqualTo("Fight Club");
                assertThat(movie.getVoteAverage()).isEqualTo(8.4);
                assertThat(movie.getPosterPath()).isEqualTo("/p.jpg");
            });
    }

    /**
     * Detail lookups are read-through/save-through against MongoDB (unlike
     * list endpoints, which always ask TMDB live): a second identical
     * request must return the same shape while WireMock records exactly one
     * upstream hit — the guarantee that repeated browsing of the same movie
     * doesn't multiply TMDB traffic.
     */
    @Test
    @DisplayName("Second identical movie-detail request is served from MongoDB — zero further TMDB calls")
    void secondDetailRequestServedFromMongo() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/movie/550"))
            .willReturn(okJson(MOVIE_550_DETAILS)));

        mockMvc.perform(get("/movie/550"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Fight Club"));

        mockMvc.perform(get("/movie/550"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Fight Club"))
            .andExpect(jsonPath("$.budget").value(63000000));

        verify(1, getRequestedFor(urlPathEqualTo("/movie/550")));
    }

    /**
     * The React app requests details as {@code /movie/{id}?append_to_response=
     * videos,credits}; the facade must fetch (and persist) both sub-resources
     * and embed them on the SAME response object, matching TMDB's own
     * append_to_response merge behavior.
     */
    @Test
    @DisplayName("Movie details with append_to_response embeds videos and credits")
    void movieDetailsWithAppendToResponse() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/movie/550"))
            .willReturn(okJson(MOVIE_550_DETAILS)));
        stubFor(WireMock.get(urlPathEqualTo("/movie/550/videos"))
            .willReturn(okJson(MOVIE_550_VIDEOS)));
        stubFor(WireMock.get(urlPathEqualTo("/movie/550/credits"))
            .willReturn(okJson(MOVIE_550_CREDITS)));

        mockMvc.perform(get("/movie/550").queryParam("append_to_response", "videos,credits"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Fight Club"))
            .andExpect(jsonPath("$.videos.results[0].key").value("abc123"))
            .andExpect(jsonPath("$.credits.cast[0].name").value("Edward Norton"));

        // Fetched sub-resources are persisted alongside the movie …
        assertThat(movieRepository.findByTmdbId(550L)).get().satisfies(movie -> {
            assertThat(movie.getVideos()).hasSize(1);
            assertThat(movie.getCredits().getCast()).hasSize(1);
        });

        // … so a second append_to_response request needs no further TMDB calls.
        mockMvc.perform(get("/movie/550").queryParam("append_to_response", "videos,credits"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.videos.results[0].key").value("abc123"));

        verify(1, getRequestedFor(urlPathEqualTo("/movie/550/videos")));
        verify(1, getRequestedFor(urlPathEqualTo("/movie/550/credits")));
    }

    /**
     * A real TMDB 404 must be replayed with both its status and its exact
     * error body (captured from {@link org.springframework.web.client.RestClientResponseException}),
     * so the React app sees TMDB's own error shape.
     */
    @Test
    @DisplayName("TMDB 404 error body passes through verbatim")
    void tmdbNotFoundPassesThrough() throws Exception {
        String tmdbError = "{\"success\":false,\"status_code\":34,"
            + "\"status_message\":\"The resource you requested could not be found.\"}";

        stubFor(WireMock.get(urlPathEqualTo("/movie/99999999"))
            .willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody(tmdbError)));

        mockMvc.perform(get("/movie/99999999"))
            .andExpect(status().isNotFound())
            .andExpect(content().json(tmdbError, JsonCompareMode.STRICT));
    }

    /**
     * A {@code /movie/{x}} where x is neither a known category nor a numeric
     * id is malformed, so the facade must answer a TMDB-shaped 404 LOCALLY
     * and never call TMDB — spending a rate-limit token on a request that
     * can't succeed would be wasteful.
     */
    @Test
    @DisplayName("Unknown movie category yields TMDB-shaped 404 without calling TMDB")
    void unknownCategoryIsLocal404() throws Exception {
        mockMvc.perform(get("/movie/not_a_category"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status_code").value(34));

        verify(0, anyRequestedFor(anyUrl()));
    }

    /**
     * The single {@code /discover/movie} endpoint backs two distinct app
     * features — genre browsing ({@code with_genres}) and actor filmography
     * ({@code with_cast}) — so both filter params must be forwarded.
     */
    @Test
    @DisplayName("Discover by genre and by cast are both forwarded")
    void discoverForwardsFilters() throws Exception {
        String discoverBody = "{\"page\":1,\"results\":[],\"total_pages\":0,\"total_results\":0}";

        stubFor(WireMock.get(urlPathEqualTo("/discover/movie"))
            .willReturn(okJson(discoverBody)));

        mockMvc.perform(get("/discover/movie")
                .queryParam("with_genres", "28").queryParam("page", "1"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/discover/movie")
                .queryParam("with_cast", "819").queryParam("page", "1"))
            .andExpect(status().isOk());

        verify(1, getRequestedFor(urlPathEqualTo("/discover/movie"))
            .withQueryParam("with_genres", equalTo("28")));
        verify(1, getRequestedFor(urlPathEqualTo("/discover/movie"))
            .withQueryParam("with_cast", equalTo("819")));
    }

    /**
     * Regression guard ported from the pre-ADR-010 facade: a query with a
     * space ("fight club") must reach TMDB decoded, not double-encoded.
     */
    @Test
    @DisplayName("Search queries with spaces reach TMDB intact")
    void searchQueryEncodingSurvivesForwarding() throws Exception {
        String emptyList = "{\"page\":1,\"results\":[],\"total_pages\":0,\"total_results\":0}";

        stubFor(WireMock.get(urlPathEqualTo("/search/movie"))
            .withQueryParam("query", equalTo("fight club"))
            .willReturn(okJson(emptyList)));

        mockMvc.perform(get("/search/movie").queryParam("query", "fight club"))
            .andExpect(status().isOk());

        verify(1, getRequestedFor(urlPathEqualTo("/search/movie"))
            .withQueryParam("query", equalTo("fight club")));
    }

    /**
     * The genre catalog (React app sidebar) must come back in TMDB's shape —
     * a bare {@code {"genres": [...]}} envelope, not the native API's
     * ApiResponse wrapper.
     */
    @Test
    @DisplayName("Genre list is TMDB-shaped")
    void genreListIsTmdbShaped() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/genre/movie/list"))
            .willReturn(okJson("{\"genres\":[{\"id\":28,\"name\":\"Action\"}]}")));

        mockMvc.perform(get("/genre/movie/list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.genres[0].id").value(28))
            .andExpect(jsonPath("$.genres[0].name").value("Action"));
    }
}
