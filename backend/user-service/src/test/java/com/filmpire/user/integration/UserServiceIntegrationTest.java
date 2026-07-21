package com.filmpire.user.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
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
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Tokens captured along the journey and reused by later steps. */
    private static String accessToken;
    private static String refreshToken;

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
        accessToken = data.get("accessToken").asText();
        refreshToken = data.get("refreshToken").asText();
    }

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
        accessToken = data.get("accessToken").asText();
        refreshToken = data.get("refreshToken").asText();

        // Rotation issued a DIFFERENT refresh token…
        assertThat(refreshToken).isNotEqualTo(oldRefreshToken);

        // …and the old one is dead on arrival.
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    @DisplayName("Password change: old password stops working, new one logs in")
    void passwordChange() throws Exception {
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
