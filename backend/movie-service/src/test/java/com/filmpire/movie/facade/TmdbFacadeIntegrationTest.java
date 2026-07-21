package com.filmpire.movie.facade;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the TMDB v3 facade: full controller → service →
 * MongoDB → (WireMock-simulated) TMDB stack.
 *
 * <p>These tests prove the facade contract from ARCHITECTURE.md §5.1:</p>
 * <ol>
 *   <li>responses are byte-shape identical to what TMDB returned,</li>
 *   <li>the client-sent {@code api_key} is stripped and replaced by the
 *       server-side key,</li>
 *   <li>save-through works — a second identical request is served from
 *       MongoDB with ZERO calls to TMDB,</li>
 *   <li>TMDB error responses pass through verbatim.</li>
 * </ol>
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

    /** Exact body our fake TMDB returns — assertions compare byte-for-byte. */
    private static final String POPULAR_PAGE_1 = """
        {"page":1,"results":[{"adult":false,"backdrop_path":"/x.jpg","genre_ids":[28,12],\
        "id":550,"original_language":"en","original_title":"Fight Club",\
        "overview":"An insomniac...","popularity":61.4,"poster_path":"/p.jpg",\
        "release_date":"1999-10-15","title":"Fight Club","video":false,\
        "vote_average":8.4,"vote_count":26280}],"total_pages":500,"total_results":10000}""";

    private static final String MOVIE_550_DETAILS = """
        {"adult":false,"budget":63000000,"genres":[{"id":18,"name":"Drama"}],"id":550,\
        "title":"Fight Club","videos":{"results":[{"key":"abc","site":"YouTube"}]},\
        "credits":{"cast":[{"id":819,"name":"Edward Norton"}]}}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TmdbDocumentRepository repository;

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
     * Starts every test from a clean slate: no stored raw documents and no
     * recorded WireMock requests (stubs are re-declared per test).
     */
    @BeforeEach
    void cleanSlate() {
        repository.deleteAll();
        resetAllRequests();
    }

    @Test
    @DisplayName("Facade returns TMDB's body byte-for-byte and strips the client api_key")
    void popularListIsByteIdenticalAndKeyIsSwapped() throws Exception {
        // Given: fake TMDB serves the fixture for the SERVER key only
        stubFor(WireMock.get(urlPathEqualTo("/movie/popular"))
            .withQueryParam("api_key", equalTo("server-side-key"))
            .willReturn(okJson(POPULAR_PAGE_1)));

        // When: the React app calls with ITS OWN api_key (must be discarded)
        mockMvc.perform(get("/movie/popular")
                .queryParam("page", "1")
                .queryParam("api_key", "react-app-client-key"))
            // Then: exact TMDB body comes back
            .andExpect(status().isOk())
            .andExpect(content().json(POPULAR_PAGE_1, true));

        // And: TMDB never saw the client's key
        verify(1, getRequestedFor(urlPathEqualTo("/movie/popular"))
            .withQueryParam("api_key", equalTo("server-side-key")));
        verify(0, getRequestedFor(urlPathEqualTo("/movie/popular"))
            .withQueryParam("api_key", equalTo("react-app-client-key")));
    }

    @Test
    @DisplayName("Second identical request is served from MongoDB — zero TMDB calls")
    void secondRequestServedFromMongo() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/movie/popular"))
            .willReturn(okJson(POPULAR_PAGE_1)));

        // 1. First request populates MongoDB via save-through.
        mockMvc.perform(get("/movie/popular").queryParam("page", "1"))
            .andExpect(status().isOk());

        // 2. Second request must be identical AND must not hit TMDB again.
        mockMvc.perform(get("/movie/popular").queryParam("page", "1"))
            .andExpect(status().isOk())
            .andExpect(content().json(POPULAR_PAGE_1, true));

        verify(1, getRequestedFor(urlPathEqualTo("/movie/popular")));
    }

    @Test
    @DisplayName("Movie details forward append_to_response and return the exact body")
    void movieDetailsWithAppendToResponse() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/movie/550"))
            .withQueryParam("append_to_response", equalTo("videos,credits"))
            .willReturn(okJson(MOVIE_550_DETAILS)));

        // The React app's details call: /movie/{id}?append_to_response=videos,credits
        mockMvc.perform(get("/movie/550")
                .queryParam("append_to_response", "videos,credits"))
            .andExpect(status().isOk())
            .andExpect(content().json(MOVIE_550_DETAILS, true));

        verify(1, getRequestedFor(urlPathEqualTo("/movie/550"))
            .withQueryParam("append_to_response", equalTo("videos,credits")));
    }

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
            .andExpect(content().json(tmdbError, true));
    }

    @Test
    @DisplayName("Unknown movie category yields TMDB-shaped 404 without calling TMDB")
    void unknownCategoryIsLocal404() throws Exception {
        mockMvc.perform(get("/movie/not_a_category"))
            .andExpect(status().isNotFound())
            .andExpect(content().json(
                "{\"success\":false,\"status_code\":34}", false));

        // The bad path never leaves the service.
        verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("Discover by genre and by cast are both forwarded verbatim")
    void discoverForwardsFilters() throws Exception {
        String discoverBody = "{\"page\":1,\"results\":[],\"total_pages\":0,\"total_results\":0}";

        stubFor(WireMock.get(urlPathEqualTo("/discover/movie"))
            .willReturn(okJson(discoverBody)));

        // Genre browsing (Sidebar) …
        mockMvc.perform(get("/discover/movie")
                .queryParam("with_genres", "28").queryParam("page", "1"))
            .andExpect(status().isOk());

        // … and actor filmography (Actor page) go through the same endpoint.
        mockMvc.perform(get("/discover/movie")
                .queryParam("with_cast", "819").queryParam("page", "1"))
            .andExpect(status().isOk());

        verify(1, getRequestedFor(urlPathEqualTo("/discover/movie"))
            .withQueryParam("with_genres", equalTo("28")));
        verify(1, getRequestedFor(urlPathEqualTo("/discover/movie"))
            .withQueryParam("with_cast", equalTo("819")));
    }

    @Test
    @DisplayName("Genre list is served and persisted")
    void genreListWorks() throws Exception {
        String genres = "{\"genres\":[{\"id\":28,\"name\":\"Action\"},{\"id\":18,\"name\":\"Drama\"}]}";

        stubFor(WireMock.get(urlPathEqualTo("/genre/movie/list"))
            .willReturn(okJson(genres)));

        mockMvc.perform(get("/genre/movie/list"))
            .andExpect(status().isOk())
            .andExpect(content().json(genres, true));

        // Save-through persisted the raw document under its canonical key.
        org.assertj.core.api.Assertions.assertThat(
            repository.findById("genre/movie/list")).isPresent();
    }
}
