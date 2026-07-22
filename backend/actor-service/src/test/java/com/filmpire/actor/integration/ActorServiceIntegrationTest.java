package com.filmpire.actor.integration;

import com.filmpire.actor.repository.ActorRepository;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.json.JsonCompareMode;

/**
 * Integration tests for actor-service: full controller → service →
 * PostgreSQL → (WireMock-simulated) TMDB stack, covering both the native
 * {@code /api/v1/actors} API and the TMDB-shaped facade.
 *
 * <p>As of ADR-010 (supersedes ADR-003), the facade serves TMDB's exact
 * field names/shape backed by the SAME persisted {@code actors} table the
 * native API reads/writes — not a raw cached copy of TMDB's bytes. So unlike
 * the pre-ADR-010 version of this file, facade assertions here check the
 * TMDB-shaped fields the React app actually reads (jsonPath) rather than
 * full byte-for-byte body equality — some fields (e.g. TMDB's
 * {@code deathday}) aren't in the typed model and are deliberately not
 * round-tripped. Upstream TMDB error bodies ARE still replayed verbatim
 * (captured from {@link org.springframework.web.client.RestClientResponseException}),
 * so those assertions stay byte-for-byte.</p>
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
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    /** A TMDB person-details fixture reused by several tests. */
    private static final String PERSON_819 = """
        {"adult":false,"biography":"Edward Harrison Norton...","birthday":"1969-08-18",\
        "deathday":null,"gender":2,"id":819,"known_for_department":"Acting",\
        "name":"Edward Norton","place_of_birth":"Boston, Massachusetts, USA",\
        "popularity":9.1,"profile_path":"/e.jpg","also_known_as":["Edward Harrison Norton"],\
        "imdb_id":"nm0001570","homepage":null}""";

    private static final String CREDITS_819 = """
        {"cast":[{"id":550,"title":"Fight Club","character":"The Narrator",\
        "release_date":"1999-10-15","poster_path":"/p.jpg","vote_average":8.4},\
        {"id":1546,"title":"25th Hour","character":"Monty Brogan",\
        "release_date":"2002-12-16","poster_path":"/q.jpg","vote_average":7.6}],"id":819}""";

    @Autowired
    private MockMvc mockMvc;

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

    /** Clean slate per test: no typed rows, no recorded requests. */
    @BeforeEach
    void cleanSlate() {
        actorRepository.deleteAll();
        resetAllRequests();
    }

    /**
     * The facade's two load-bearing promises for the detail endpoint: TMDB's
     * own field names come back (the fields our typed model captures) and
     * the client-supplied api_key is discarded in favor of the server key.
     */
    @Test
    @DisplayName("Facade /person/{id}: TMDB-shaped fields, client api_key stripped")
    void personFacadeIsTmdbShapedAndKeyIsSwapped() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/person/819"))
            .withQueryParam("api_key", equalTo("server-side-key"))
            .willReturn(okJson(PERSON_819)));

        mockMvc.perform(get("/person/819").queryParam("api_key", "react-app-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(819))
            .andExpect(jsonPath("$.name").value("Edward Norton"))
            .andExpect(jsonPath("$.place_of_birth").value("Boston, Massachusetts, USA"))
            .andExpect(jsonPath("$.known_for_department").value("Acting"))
            .andExpect(jsonPath("$.also_known_as[0]").value("Edward Harrison Norton"));

        verify(1, getRequestedFor(urlPathEqualTo("/person/819"))
            .withQueryParam("api_key", equalTo("server-side-key")));
        verify(0, getRequestedFor(urlPathEqualTo("/person/819"))
            .withQueryParam("api_key", equalTo("react-app-key")));
    }

    /**
     * Proves ADR-010's central claim for actor-service: the profile a facade
     * request returns is genuinely persisted, not just cached bytes.
     * Querying PostgreSQL directly must find the same actor.
     */
    @Test
    @DisplayName("Facade save-through: second request served from PostgreSQL, zero further TMDB calls")
    void personServedFromPostgresOnRepeat() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/person/819"))
            .willReturn(okJson(PERSON_819)));

        mockMvc.perform(get("/person/819")).andExpect(status().isOk());
        assertThat(actorRepository.findById(819L))
            .isPresent()
            .get()
            .satisfies(actor -> assertThat(actor.getName()).isEqualTo("Edward Norton"));

        mockMvc.perform(get("/person/819"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Edward Norton"));

        verify(1, getRequestedFor(urlPathEqualTo("/person/819")));
    }

    /**
     * The facade's {@code /person/{id}/movie_credits} endpoint (backing
     * TMDB's own filmography shape) is always live — the referenced movies
     * are movie-service's data (ADR-002), so there's nothing here to persist
     * or save-through.
     */
    @Test
    @DisplayName("Facade /person/{id}/movie_credits is TMDB-shaped")
    void personMovieCreditsFacadeIsTmdbShaped() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/person/819/movie_credits"))
            .willReturn(okJson(CREDITS_819)));

        mockMvc.perform(get("/person/819/movie_credits"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cast.length()").value(2))
            .andExpect(jsonPath("$.cast[0].title").value("Fight Club"))
            .andExpect(jsonPath("$.cast[0].character").value("The Narrator"));
    }

    /**
     * Two error paths that must stay distinct: a real TMDB 404 is replayed
     * byte-for-byte (captured from RestClientResponseException — the app
     * sees TMDB's own error shape), while a structurally-invalid id
     * (non-numeric) is rejected locally and must never reach TMDB.
     */
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
            .andExpect(content().json(tmdbError, JsonCompareMode.STRICT));

        // Non-numeric id → rejected locally, TMDB never called.
        mockMvc.perform(get("/person/not-a-person"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status_code").value(34));
        verify(0, getRequestedFor(urlPathEqualTo("/person/not-a-person")));
    }

    /**
     * The native API parses the same TMDB response the facade maps and
     * projects the fields the app needs (id, name, birthplace, birthdate).
     * The follow-up repository check proves the upsert side effect fires,
     * giving the service a locally-queryable actors table that grows with
     * use. The {@code _links} assertions pin down the HATEOAS contract on
     * the profile resource: self plus a filmography relation, matching the
     * sub-resource the controller actually exposes.
     */
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
            .andExpect(jsonPath("$.data.birthDate").value("1969-08-18"))
            .andExpect(jsonPath("$.data.knownForDepartment").value("Acting"))
            .andExpect(jsonPath("$.data.imdbId").value("nm0001570"))
            // HATEOAS: the profile advertises self + filmography links so a
            // client discovers navigation instead of building URLs.
            .andExpect(jsonPath("$.data._links.self.href", containsString("/api/v1/actors/819")))
            .andExpect(jsonPath("$.data._links.movies.href", containsString("/api/v1/actors/819/movies")));

        // Side effect: the typed row exists for local querying.
        assertThat(actorRepository.findById(819L))
            .isPresent()
            .get()
            .satisfies(actor -> assertThat(actor.getName()).isEqualTo("Edward Norton"));
    }

    /**
     * Filmography is derived from TMDB's movie_credits cast and must be sorted
     * newest-release-first (the order the UI expects). The fixture deliberately
     * lists Fight Club (1999) before 25th Hour (2002) so a green result proves
     * the service re-sorts rather than passing TMDB's order through.
     */
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

    /**
     * Regression guard ported from movie-service: an accented query ("léa
     * seydoux") must reach TMDB decoded, not double-encoded. WireMock matches
     * on the decoded value, so a match confirms the typed HTTP-interface
     * client (Spring's own URI-template encoding) forwards non-ASCII search
     * terms correctly — same guarantee the old hand-rolled raw client's
     * explicit percent-encoding used to provide.
     */
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

    /**
     * Proves search results are upserted, mirroring movie-service's list
     * endpoints: a search hit becomes a locally-queryable (if lightweight)
     * actor row without ever hitting the detail endpoint.
     */
    @Test
    @DisplayName("Native search: results are upserted into PostgreSQL")
    void nativeSearchUpsertsResults() throws Exception {
        String searchResult = """
            {"page":1,"results":[{"id":1245,"name":"Léa Seydoux",\
            "profile_path":"/l.jpg","popularity":12.3}],"total_pages":1,"total_results":1}""";

        stubFor(WireMock.get(urlPathEqualTo("/search/person"))
            .willReturn(okJson(searchResult)));

        mockMvc.perform(get("/api/v1/actors/search").queryParam("query", "seydoux"))
            .andExpect(status().isOk());

        assertThat(actorRepository.findById(1245L))
            .isPresent()
            .get()
            .satisfies(actor -> {
                assertThat(actor.getName()).isEqualTo("Léa Seydoux");
                assertThat(actor.getProfilePath()).isEqualTo("/l.jpg");
            });
    }

    /**
     * The same upstream 404 that the facade replays raw must, on the native
     * path, be translated into the shared ApiResponse envelope (success:false)
     * — proving the package-scoped error advice keeps native errors
     * ApiResponse-shaped while leaving the facade's raw TMDB errors untouched.
     */
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
