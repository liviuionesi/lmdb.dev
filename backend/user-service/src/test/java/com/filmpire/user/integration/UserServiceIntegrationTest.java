package com.filmpire.user.integration;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests against real PostgreSQL (Testcontainers):
 * Flyway migration, registration, login, JWT-protected profile access,
 * favorites/watchlist round trips, password change, refresh-token rotation
 * and logout revocation — the complete issue #17 acceptance flow.
 *
 * <p>Ordered as one user journey: each step builds on server state created
 * by the previous ones (deliberate — the journey IS the scenario).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("User Service Integration Tests (PostgreSQL via Testcontainers)")
class UserServiceIntegrationTest {

    /** Real PostgreSQL 17; @ServiceConnection wires the datasource. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Tokens captured along the journey and reused by later steps. */
    private static String accessToken;
    private static String refreshToken;

    /**
     * Journey step 1: a real INSERT through the Flyway-migrated schema. The
     * captured access/refresh tokens seed every later step, so this also
     * proves signup returns both tokens without a follow-up login call.
     */
    @Test
    @Order(1)
    @DisplayName("Register: creates the account and returns a token bundle")
    void register() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"liviu","email":"liviu@example.com","password":"top-secret-1"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.data.user.username").value("liviu"))
            .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        accessToken = data.get("accessToken").asString();
        refreshToken = data.get("refreshToken").asString();
    }

    /**
     * Re-registering the username from step 1 (different email) must map to a
     * clean 400 envelope — proof the guard fires in the service layer rather
     * than leaking PostgreSQL's unique-constraint violation as a 500.
     */
    @Test
    @Order(2)
    @DisplayName("Register: duplicate username is rejected with 400")
    void registerDuplicateRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"liviu","email":"other@example.com","password":"top-secret-1"}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Exercises the real security chain, not a mocked one: the anonymous
     * request must die in the JWT filter, and the step-1 token must resolve
     * to the same account that registered — identity, not just validity.
     */
    @Test
    @Order(3)
    @DisplayName("Profile requires a token: 401 without, 200 with")
    void profileRequiresToken() throws Exception {
        // No token → the security chain must reject before any controller.
        mockMvc.perform(get("/api/v1/users/profile"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/profile")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("liviu"))
            .andExpect(jsonPath("$.data.email").value("liviu@example.com"));
    }

    /**
     * Idempotency here rests on a real (user, movie) unique constraint, so it
     * only proves out against actual PostgreSQL: the double add must collapse
     * to one row, and ordering/deletion must hold across real queries.
     */
    @Test
    @Order(4)
    @DisplayName("Favorites: add is idempotent, list is newest-first, delete removes")
    void favoritesRoundTrip() throws Exception {
        // Add twice — second add must be a silent no-op (idempotency).
        mockMvc.perform(post("/api/v1/users/favorites/550")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users/favorites/550")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users/favorites/278")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk());

        // 550 added first → 278 leads the newest-first list; no duplicate 550.
        mockMvc.perform(get("/api/v1/users/favorites")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].movieId").value(278));

        mockMvc.perform(delete("/api/v1/users/favorites/278")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/favorites")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(jsonPath("$.data.length()").value(1));
    }

    /**
     * Favorites and watchlist share the same (user, movie) shape, so a
     * mis-joined or shared table is a plausible bug: with step 4's favorite
     * still present, a count of exactly 1 proves the lists never bleed.
     */
    @Test
    @Order(5)
    @DisplayName("Watchlist: independent of favorites")
    void watchlistRoundTrip() throws Exception {
        mockMvc.perform(post("/api/v1/users/watchlist/603")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk());

        // Watchlist has its own single entry — favorites' state is separate.
        mockMvc.perform(get("/api/v1/users/watchlist")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].movieId").value(603));
    }

    /**
     * Rotation is the replay defense: every refresh must mint a NEW refresh
     * token and kill the presented one, so a stolen token becomes worthless
     * the moment its legitimate owner refreshes first.
     */
    @Test
    @Order(6)
    @DisplayName("Refresh rotates: new tokens issued, old refresh token dies")
    void refreshRotation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andReturn();

        String oldRefreshToken = refreshToken;
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        accessToken = data.get("accessToken").asString();
        refreshToken = data.get("refreshToken").asString();

        // Rotation issued a DIFFERENT refresh token…
        assertThat(refreshToken).isNotEqualTo(oldRefreshToken);

        // …and the old one is dead on arrival.
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * The change must take effect within the request that reported OK: the
     * old hash has to be unusable and the new one live immediately, with no
     * window where both (or neither) credential authenticates.
     */
    @Test
    @Order(7)
    @DisplayName("Password change: old password stops working, new one logs in")
    void passwordChange() throws Exception {
        // 1. Authenticated change swaps the stored hash.
        mockMvc.perform(put("/api/v1/users/password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"top-secret-1","newPassword":"top-secret-2"}"""))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"liviu\",\"password\":\"top-secret-1\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"liviu\",\"password\":\"top-secret-2\"}"))
            .andExpect(status().isOk());
    }

    /**
     * Logout must sever the renewable half of the session: the refresh token
     * dies immediately, but the already-issued access token stays valid until
     * its short natural expiry. This asserts that accepted stateless-JWT
     * trade-off explicitly, so a future "revoke access tokens on logout"
     * change is a conscious decision rather than a silent regression.
     */
    @Test
    @Order(8)
    @DisplayName("Logout revokes the refresh token; access token expires naturally")
    void logoutRevokesRefreshTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk());

        // The refresh token from step 6 is revoked…
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isUnauthorized());

        // …while the short-lived access token keeps working until expiry —
        // the documented stateless-JWT trade-off (failure-mode matrix).
        mockMvc.perform(get("/api/v1/users/profile")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk());
    }

    /**
     * Bean-validation failures (short username, malformed email, short
     * password all at once) must surface as a 400 with a populated message,
     * not a stack trace or 500 — proof the GlobalExceptionHandler translates
     * MethodArgumentNotValidException into the shared error envelope.
     */
    @Test
    @Order(9)
    @DisplayName("Validation: malformed registration is rejected with field details")
    void validationErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"ab","email":"not-an-email","password":"short"}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
