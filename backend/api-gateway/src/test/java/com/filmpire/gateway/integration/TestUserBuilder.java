package com.filmpire.gateway.integration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Test-data builder for a Filmpire user account, used by the full-stack journey
 * suite ({@link FullStackJourneyIT}) — the "create test data builders" item of
 * issue #19.
 *
 * <p>Each build registers a genuinely unique account through the running
 * gateway (unique username + email via a random suffix), so repeated test runs
 * never collide on the unique constraints in user-service's PostgreSQL and no
 * cleanup is required between runs. The returned {@link RegisteredUser} carries
 * the freshly minted access token, so a journey test can immediately act as
 * that authenticated user.</p>
 *
 * <p>This is a real, reusable builder rather than an inline payload: journeys
 * that need a user say {@code TestUserBuilder.aUser().registerVia(client)} and
 * get back an authenticated handle, with the registration mechanics (payload
 * shape, token extraction) in one place.</p>
 */
final class TestUserBuilder {

    /** Shared parser for the auth responses (Boot 4 / Jackson 3). */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Password used for every built user — meets the service's complexity rule. */
    private static final String DEFAULT_PASSWORD = "TestPass123!";

    private String usernamePrefix = "it_user";
    private String password = DEFAULT_PASSWORD;

    private TestUserBuilder() {
    }

    /**
     * Starts a new builder with sensible defaults.
     *
     * @return a fresh builder
     */
    static TestUserBuilder aUser() {
        return new TestUserBuilder();
    }

    /**
     * Overrides the username prefix (the random uniqueness suffix is always
     * appended regardless), useful when a test wants a recognizable name in the
     * database while debugging.
     *
     * @param prefix human-readable username prefix
     * @return this builder
     */
    TestUserBuilder withUsernamePrefix(String prefix) {
        this.usernamePrefix = prefix;
        return this;
    }

    /**
     * The credentials/handle of an account that has been registered through the
     * gateway, including the access token minted at registration.
     *
     * @param username the unique username
     * @param email    the unique email
     * @param password the plaintext password (for a later login if needed)
     * @param userId   the server-assigned user id
     * @param accessToken a currently-valid bearer token for this user
     */
    record RegisteredUser(String username, String email, String password,
                          String userId, String accessToken) {

        /**
         * Convenience for setting the Authorization header on a request.
         *
         * @return the {@code Bearer <token>} header value
         */
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    /**
     * Registers this user through the real gateway and returns an authenticated
     * handle.
     *
     * <p>Asserts a 201 with the documented {@code ApiResponse} envelope, then
     * extracts the access token and user id — failing loudly if the auth
     * contract the rest of the suite depends on is not met.</p>
     *
     * @param client a {@link WebTestClient} bound to the running gateway
     * @return the registered, authenticated user
     */
    RegisteredUser registerVia(WebTestClient client) {
        // 1. Unique identity so concurrent/repeat runs never collide.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = usernamePrefix + "_" + suffix;
        String email = username + "@filmpire-it.test";

        String body = """
            {"username":"%s","email":"%s","password":"%s"}
            """.formatted(username, email, password);

        // 2. Register through the gateway → user-service → PostgreSQL.
        String response = client.post().uri("/api/v1/auth/register")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        // 3. Pull the token + id out of the ApiResponse envelope.
        JsonNode data = JSON.readTree(response).path("data");
        String accessToken = data.path("accessToken").asString();
        String userId = data.path("user").path("id").asString();

        return new RegisteredUser(username, email, password, userId, accessToken);
    }
}
