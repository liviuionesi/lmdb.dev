package com.filmpire.gateway.integration;

import com.filmpire.gateway.integration.TestUserBuilder.RegisteredUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Full-stack cross-service journey tests for issue #19 (Service Integration
 * Testing) — the "data flow across services" half of that issue, complementing
 * the WireMock-backed {@link GatewayIntegrationTest} which proves routing/policy
 * wiring in isolation.
 *
 * <p>Where {@code GatewayIntegrationTest} boots the gateway alone and points its
 * routes at a WireMock stand-in, this suite is a pure black-box client against
 * the <b>whole running stack</b> (gateway + user/movie/actor services + their
 * PostgreSQL/MongoDB/Redis, wired through Eureka). It exercises the exact
 * user journeys the #19 checklist names, at the layer where they are actually
 * real: not a service-to-service DB join (ADR-002 forbids those), but the API
 * composition the Filmpire React app performs through the gateway.</p>
 *
 * <p><b>Runs only when the stack is up.</b> Under database-per-service there is
 * no way to spin all five services inside a single Testcontainers test cheaply,
 * so this suite targets a stack already running on
 * {@code ${FILMPIRE_GATEWAY_URL:http://localhost:8080}} (the podman-compose
 * stack). If the gateway health check is unreachable, every test is skipped via
 * a JUnit assumption rather than failed — so {@code ./gradlew build} stays green
 * on a machine with no stack, while a developer (or the #19 acceptance run) gets
 * genuine end-to-end coverage when the stack is up.</p>
 *
 * <p>Journeys covered:</p>
 * <ul>
 *   <li><b>Movie–User</b> — register → add favorite → read favorites → add
 *       watchlist → read watchlist → remove favorite → confirm removed, all
 *       through the gateway to user-service and back.</li>
 *   <li><b>Movie–Actor</b> — actor details, actor filmography, and
 *       "movies by this actor" (discover with_cast), spanning actor-service and
 *       movie-service.</li>
 *   <li><b>Movie-with-cast</b> — a movie detail request whose cast is filled in
 *       via TMDB's {@code append_to_response=credits}.</li>
 *   <li><b>Auth boundary</b> — a protected route rejects an unauthenticated
 *       caller before any downstream is touched.</li>
 * </ul>
 */
@DisplayName("Full-Stack Journey Tests (#19, live stack)")
class FullStackJourneyIT {

    /** Base URL of the running gateway; overridable for a non-default host/port. */
    private static final String GATEWAY_URL =
            System.getenv().getOrDefault("FILMPIRE_GATEWAY_URL", "http://localhost:8080");

    /** Set once in {@link #verifyStackIsReachable()}; gates the whole suite. */
    private static boolean stackUp;

    private WebTestClient client;

    /**
     * Probes the gateway's health endpoint once before the suite. A reachable,
     * 2xx health response means the full stack is (at least) routable; anything
     * else (connection refused, timeout, non-2xx) leaves {@link #stackUp} false
     * so every test aborts as <em>skipped</em>, never failed.
     */
    @BeforeAll
    static void verifyStackIsReachable() {
        try (HttpClient probe = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build()) {
            HttpResponse<Void> health = probe.send(
                    HttpRequest.newBuilder(URI.create(GATEWAY_URL + "/actuator/health"))
                            .timeout(Duration.ofSeconds(3))
                            .GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            stackUp = health.statusCode() >= 200 && health.statusCode() < 300;
        } catch (Exception e) {
            // Connection refused / timeout / interrupted: stack is not up.
            stackUp = false;
        }
    }

    /**
     * Skips the test when the stack is down and, otherwise, binds a
     * {@link WebTestClient} to the live gateway with a generous timeout (real
     * network + real services are slower than an in-JVM WireMock).
     */
    @BeforeEach
    void setUp() {
        assumeTrue(stackUp,
                "Full stack not reachable at " + GATEWAY_URL + " — skipping live journey tests. "
                        + "Start it with `podman-compose -f infrastructure/docker/docker-compose.yml up -d`.");
        client = WebTestClient.bindToServer()
                .baseUrl(GATEWAY_URL)
                .responseTimeout(Duration.ofSeconds(20))
                .build();
    }

    /**
     * The Movie–User favorites/watchlist journey end to end. Proves the write
     * path (gateway → JWT filter → user-service → PostgreSQL) and the read-back
     * are consistent, and that a removal actually takes effect — the account
     * feature that is the whole reason user-service exists.
     */
    @Test
    @DisplayName("Movie–User: favorites & watchlist round-trip through the gateway")
    void favoritesAndWatchlistRoundTrip() {
        // Given a freshly registered, authenticated user
        RegisteredUser user = TestUserBuilder.aUser().registerVia(client);
        long fightClub = 550L;
        long inception = 27205L;

        // When the user favorites Fight Club
        client.post().uri("/api/v1/users/favorites/{id}", fightClub)
                .header(HttpHeaders.AUTHORIZATION, user.bearer())
                .exchange()
                .expectStatus().isOk();

        // Then it comes back in their favorites
        client.get().uri("/api/v1/users/favorites")
                .header(HttpHeaders.AUTHORIZATION, user.bearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[?(@.movieId == %d)]".formatted(fightClub)).exists();

        // And when they add Inception to their watchlist, it is listed there
        client.post().uri("/api/v1/users/watchlist/{id}", inception)
                .header(HttpHeaders.AUTHORIZATION, user.bearer())
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/api/v1/users/watchlist")
                .header(HttpHeaders.AUTHORIZATION, user.bearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[?(@.movieId == %d)]".formatted(inception)).exists();

        // And when they remove the favorite, it is gone (the removal is real,
        // not just a 200)
        client.delete().uri("/api/v1/users/favorites/{id}", fightClub)
                .header(HttpHeaders.AUTHORIZATION, user.bearer())
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/api/v1/users/favorites")
                .header(HttpHeaders.AUTHORIZATION, user.bearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[?(@.movieId == %d)]".formatted(fightClub)).doesNotExist();
    }

    /**
     * The Movie–Actor journey the React app's actor page performs: the actor's
     * profile, their filmography, and "other movies with this actor". These span
     * actor-service (person facade) and movie-service (discover), and are the
     * closest thing to a cross-service query the architecture has — composed at
     * the API layer, not the database.
     */
    @Test
    @DisplayName("Movie–Actor: details, filmography, and movies-by-actor across services")
    void actorDetailsFilmographyAndMoviesByActor() {
        long edwardNorton = 819L;

        // Actor details (actor-service persisted facade)
        client.get().uri("/person/{id}", edwardNorton)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo((int) edwardNorton)
                .jsonPath("$.name").isNotEmpty();

        // Filmography — TMDB-shaped credits, non-empty for a working actor
        client.get().uri("/person/{id}/movie_credits", edwardNorton)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.cast").isArray()
                .jsonPath("$.cast[0].title").isNotEmpty();

        // "Movies by this actor" — the discover-with-cast path, served by
        // movie-service (ADR-002: the movies are movie-service's data)
        client.get().uri(b -> b.path("/discover/movie")
                        .queryParam("with_cast", edwardNorton)
                        .queryParam("page", 1).build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.results").isArray()
                .jsonPath("$.results[0].id").isNotEmpty();
    }

    /**
     * A movie detail request must be able to include its cast via TMDB's
     * {@code append_to_response=credits}, which movie-service fetches and
     * persists alongside the movie — the "movie with cast" scenario from the
     * #19 checklist, at the one place cast actually attaches to a movie.
     */
    @Test
    @DisplayName("Movie-with-cast: append_to_response=credits returns the cast")
    void movieDetailsIncludeCast() {
        client.get().uri(b -> b.path("/movie/550")
                        .queryParam("append_to_response", "credits").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(550)
                .jsonPath("$.credits.cast").isArray()
                .jsonPath("$.credits.cast[0].name").isNotEmpty();
    }

    /**
     * The account routes are private: an unauthenticated caller must be rejected
     * at the gateway with 401 before any downstream is reached. This is the same
     * contract {@link GatewayIntegrationTest} proves against WireMock, asserted
     * here against the real user-service to confirm the wiring holds end to end.
     */
    @Test
    @DisplayName("Auth boundary: protected user route rejects an unauthenticated caller")
    void protectedRouteRequiresAuthentication() {
        client.get().uri("/api/v1/users/favorites")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
