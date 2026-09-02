package dev.lmdb.actor.docs;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies the OpenAPI surface this service publishes (issue #18).
 *
 * <p>The dependency, the {@code springdoc.api-docs.path} property and the
 * {@code @Tag}/{@code @Operation} annotations can all be present while the endpoint still 404s —
 * springdoc only registers it when its auto-configuration actually applies, and a removed starter
 * breaks that without changing any of the three. Only a request proves the document is served.
 *
 * <p>This service exposes two APIs, the native {@code /api/v1/actors} one and the TMDB-shaped
 * facade (ADR-010), so the document is asserted to describe both.
 *
 * <p>Boots against a Testcontainers PostgreSQL, matching this module's other integration tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("OpenAPI Documentation Tests")
class OpenApiDocsTest {

  /** Real PostgreSQL 17; @ServiceConnection wires the datasource. */
  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private MockMvc mockMvc;

  /**
   * The generated document must be served and be a valid OpenAPI 3 document. {@code openapi} and
   * {@code info.title} are the two fields every client tool reads first, so an empty or error
   * response is caught here rather than in Swagger UI.
   *
   * @throws Exception if the request fails
   */
  @Test
  @DisplayName("/v3/api-docs serves an OpenAPI 3 document")
  void apiDocsAreServed() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").value(startsWith("3.")))
        .andExpect(jsonPath("$.info.title").exists());
  }

  /**
   * A document that is served but describes nothing is worse than none, because tooling reports it
   * as healthy. Both controllers are asserted: the facade paths are the contract the gateway routes
   * to, and dropping either one from {@code paths} leaves the endpoint above returning 200.
   *
   * @throws Exception if the request fails
   */
  @Test
  @DisplayName("The document describes both the native actor API and the TMDB facade")
  void apiDocsDescribeBothApis() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/actors/{id}'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/actors/{id}/movies'].get").exists())
        .andExpect(jsonPath("$.paths['/person/{id}'].get").exists())
        .andExpect(jsonPath("$.paths['/search/person'].get").exists());
  }

  /**
   * {@code springdoc.swagger-ui.path} is what a person types; springdoc answers it with a redirect
   * to the bundled UI. A 404 here means the UI dependency is missing even though the document
   * itself is served, which the two tests above would not detect.
   *
   * @throws Exception if the request fails
   */
  @Test
  @DisplayName("/swagger-ui.html redirects to the bundled Swagger UI")
  void swaggerUiIsReachable() throws Exception {
    mockMvc
        .perform(get("/swagger-ui.html"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/swagger-ui/index.html"));
  }
}
