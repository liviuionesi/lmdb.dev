package dev.lmdb.user.docs;

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
 * Verifies the OpenAPI surface this service publishes (issue #17).
 *
 * <p>This module is the one where the document can disappear without any change to springdoc:
 * {@code SecurityConfig} permits {@code /v3/api-docs/**} and {@code /swagger-ui/**} explicitly, and
 * every other route requires a token. Narrowing that rule turns the document into a 401 while the
 * dependency, the property and the annotations all still look correct. These tests call the
 * endpoints without a token, so that regression fails the build.
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
   * The generated document must be served, unauthenticated, and be a valid OpenAPI 3 document.
   * {@code openapi} and {@code info.title} are the two fields every client tool reads first.
   *
   * @throws Exception if the request fails
   */
  @Test
  @DisplayName("/v3/api-docs serves an OpenAPI 3 document without a token")
  void apiDocsAreServed() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").value(startsWith("3.")))
        .andExpect(jsonPath("$.info.title").exists());
  }

  /**
   * A document that is served but describes nothing is worse than none, because tooling reports it
   * as healthy. Asserting on the auth and profile routes proves both controllers were scanned: a
   * package move or a dropped {@code @RestController} drops them from {@code paths} while the
   * endpoint above still returns 200.
   *
   * @throws Exception if the request fails
   */
  @Test
  @DisplayName("The document describes this service's auth and profile routes")
  void apiDocsDescribeThisServicesRoutes() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/profile'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/favorites'].get").exists());
  }

  /**
   * {@code springdoc.swagger-ui.path} is what a person types; springdoc answers it with a redirect
   * to the bundled UI. A 401 or 404 here means the security rule or the UI dependency broke even
   * though the document itself is served.
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
