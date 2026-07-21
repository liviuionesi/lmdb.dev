package com.filmpire.actor.integration;

import com.filmpire.actor.facade.TmdbPersonDocumentRepository;
import com.filmpire.actor.repository.ActorRepository;
import com.github.tomakehurst.wiremock.client.WireMock;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for actor-service: TMDB person facade byte-fidelity and
 * save-through against real PostgreSQL, plus the native typed API parsing
 * the same raw documents (issues #18/#32; ARCHITECTURE.md §5.1 row 8).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@WireMockTest(httpPort = 9991)
@DisplayName("Actor Service Integration Tests (PostgreSQL + WireMock TMDB)")
class ActorServiceIntegrationTest {

    /** Real PostgreSQL 17; @ServiceConnection wires the datasource. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    /** Exact person body our fake TMDB returns — compared byte-for-byte. */
    private static final String PERSON_819 = """
        {"adult":false,"biography":"Edward Harrison Norton...","birthday":"1969-08-18",\
        "deathday":null,"gender":2,"id":819,"known_for_department":"Acting",\
        "name":"Edward Norton","place_of_birth":"Boston, Massachusetts, USA",\
        "popularity":9.1,"profile_path":"/e.jpg"}""";

    private static final String CREDITS_819 = """
        {"cast":[{"id":550,"title":"Fight Club","character":"The Narrator",\
        "release_date":"1999-10-15","poster_path":"/p.jpg","vote_average":8.4},\
        {"id":1546,"title":"25th Hour","character":"Monty Brogan",\
        "release_date":"2002-12-16","poster_path":"/q.jpg","vote_average":7.6}],"id":819}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TmdbPersonDocumentRepository documentRepository;

    @Autowired
    private ActorRepository actorRepository;

    /**
     * Points the TMDB base URL at WireMock with a fixed server key.
     *
     * @param registry Spring test property registry
     */
    @DynamicPropertySource
    static void tmdbProperties(DynamicPropertyRegistry registry) {
        registry.add("tmdb.api.base-url", () -> "http://localhost:9991");
        registry.add("tmdb.api.key", () -> "server-side-key");
    }

    /** Clean slate per test: no raw docs, no typed rows, no recorded requests. */
    @BeforeEach
    void cleanSlate() {
        documentRepository.deleteAll();
        actorRepository.deleteAll();
        resetAllRequests();
    }

    @Test
    @DisplayName("Facade /person/{id}: byte-identical body, client api_key stripped")
    void personFacadeByteFidelity() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/person/819"))
            .withQueryParam("api_key", equalTo("server-side-key"))
            .willReturn(okJson(PERSON_819)));

        mockMvc.perform(get("/person/819").queryParam("api_key", "react-app-key"))
            .andExpect(status().isOk())
            .andExpect(content().json(PERSON_819, true));

        // Server key used; client key never forwarded.
        verify(1, getRequestedFor(urlPathEqualTo("/person/819"))
            .withQueryParam("api_key", equalTo("server-side-key")));
        verify(0, getRequestedFor(urlPathEqualTo("/person/819"))
            .withQueryParam("api_key", equalTo("react-app-key")));
    }

    @Test
    @DisplayName("Facade save-through: second request served from PostgreSQL, zero TMDB calls")
    void personServedFromPostgresOnRepeat() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/person/819"))
            .willReturn(okJson(PERSON_819)));

        // 1. First request populates PostgreSQL.
        mockMvc.perform(get("/person/819")).andExpect(status().isOk());
        assertThat(documentRepository.findById("person/819")).isPresent();

        // 2. Second request: identical body, no additional TMDB traffic.
        mockMvc.perform(get("/person/819"))
            .andExpect(status().isOk())
            .andExpect(content().json(PERSON_819, true));

        verify(1, getRequestedFor(urlPathEqualTo("/person/819")));
    }

    @Test
    @DisplayName("Facade: TMDB 404 passes through verbatim; non-numeric id is a local 404")
    void personErrorHandling() throws Exception {
        String tmdbError = "{\"success\":false,\"status_code\":34,"
            + "\"status_message\":\"The resource you requested could not be found.\"}";

        stubFor(WireMock.get(urlPathEqualTo("/person/999999999"))
            .willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody(tmdbError)));

        // Upstream 404 → replayed exactly.
        mockMvc.perform(get("/person/999999999"))
            .andExpect(status().isNotFound())
            .andExpect(content().json(tmdbError, true));

        // Non-numeric id → rejected locally, TMDB never called.
        mockMvc.perform(get("/person/not-a-person"))
            .andExpect(status().isNotFound())
            .andExpect(content().json("{\"success\":false,\"status_code\":34}", false));
        verify(0, getRequestedFor(urlPathEqualTo("/person/not-a-person")));
    }

    @Test
    @DisplayName("Native /api/v1/actors/{id}: typed profile parsed and upserted")
    void nativeActorEndpointParsesAndPersists() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/person/819"))
            .willReturn(okJson(PERSON_819)));

        mockMvc.perform(get("/api/v1/actors/819"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.tmdbId").value(819))
            .andExpect(jsonPath("$.data.name").value("Edward Norton"))
            .andExpect(jsonPath("$.data.birthPlace").value("Boston, Massachusetts, USA"))
            .andExpect(jsonPath("$.data.birthDate").value("1969-08-18"));

        // Side effect: the typed row exists for local querying.
        assertThat(actorRepository.findById(819L))
            .isPresent()
            .get()
            .satisfies(actor -> assertThat(actor.getName()).isEqualTo("Edward Norton"));
    }

    @Test
    @DisplayName("Native filmography: parsed from movie_credits, newest first")
    void nativeFilmographySortedNewestFirst() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/person/819/movie_credits"))
            .willReturn(okJson(CREDITS_819)));

        mockMvc.perform(get("/api/v1/actors/819/movies"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            // 25th Hour (2002) released after Fight Club (1999) → first.
            .andExpect(jsonPath("$.data[0].movieId").value(1546))
            .andExpect(jsonPath("$.data[0].character").value("Monty Brogan"))
            .andExpect(jsonPath("$.data[1].movieId").value(550));
    }

    @Test
    @DisplayName("Native search: UTF-8 query survives forwarding (encoding regression guard)")
    void nativeSearchWithUtf8Query() throws Exception {
        String searchResult = """
            {"page":1,"results":[{"id":1245,"name":"Léa Seydoux",\
            "profile_path":"/l.jpg","popularity":12.3}],"total_pages":1,"total_results":1}""";

        // WireMock matches the DECODED value — proves no double-encoding.
        stubFor(WireMock.get(urlPathEqualTo("/search/person"))
            .withQueryParam("query", equalTo("léa seydoux"))
            .willReturn(okJson(searchResult)));

        mockMvc.perform(get("/api/v1/actors/search")
                .queryParam("query", "léa seydoux"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalResults").value(1))
            .andExpect(jsonPath("$.data.results[0].name").value("Léa Seydoux"));
    }

    @Test
    @DisplayName("Native endpoint maps upstream 404 to the ApiResponse envelope")
    void nativeEndpointWrapsUpstreamError() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/person/424242"))
            .willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status_code\":34}")));

        // Same failure, different shape than the facade: native endpoints
        // speak ApiResponse, facade endpoints speak raw TMDB.
        mockMvc.perform(get("/api/v1/actors/424242"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }
}
