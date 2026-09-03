package dev.lmdb.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lmdb.user.model.Role;
import dev.lmdb.user.repository.UserRepository;
import dev.lmdb.user.security.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Full-stack proof of the "configured" side of issue #238's acceptance criteria: with {@code
 * admin.bootstrap.*} set, {@link dev.lmdb.user.config.AdminBootstrapRunner} must provision the
 * account before the first request, and it must actually be usable — able to log in and receive a
 * JWT carrying the {@code ADMIN} role, the exact claim the gateway's {@code /admin/**} rule (#237,
 * asserted end-to-end in {@code GatewayIntegrationTest}) requires.
 *
 * <p>The "unconfigured" side (no properties set → no admin created) is asserted against this same
 * real Postgres/Spring-context setup in {@link UserServiceIntegrationTest}, since that test class
 * already boots the default (unconfigured) context.
 */
@SpringBootTest(
    properties = {
      "admin.bootstrap.username=bootadmin",
      "admin.bootstrap.email=bootadmin@example.com",
      "admin.bootstrap.password=correct-horse-battery-staple"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("ADMIN Bootstrap Integration Test (configured)")
class AdminBootstrapIntegrationTest {

  /** Real PostgreSQL 17; @ServiceConnection wires the datasource. */
  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private UserRepository userRepository;

  @Autowired private JwtTokenProvider jwtTokenProvider;

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  /**
   * Startup alone (no request made yet) must have already created the account — proves the runner
   * ran, not just that the service class works when called directly.
   */
  @Test
  @DisplayName("Configured admin account exists in the database at startup")
  void adminAccountIsProvisionedAtStartup() {
    assertThat(userRepository.findByUsername("bootadmin"))
        .isPresent()
        .get()
        .satisfies(
            user -> {
              assertThat(user.getRole()).isEqualTo(Role.ADMIN);
              assertThat(user.isEnabled()).isTrue();
            });
  }

  /**
   * Logs in with the bootstrap credentials through the real {@code /api/v1/auth/login} endpoint and
   * decodes the returned access token: it must carry {@code roles: [ADMIN]}, the claim {@code
   * GatewayIntegrationTest} proves is sufficient for a 200 on {@code /admin/**}.
   */
  @Test
  @DisplayName("Bootstrap admin can log in and receives a token carrying the ADMIN role")
  void bootstrapAdminCanAuthenticateWithAdminRole() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"username":"bootadmin","password":"correct-horse-battery-staple"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
            .andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    String accessToken = data.get("accessToken").asString();

    assertThat(jwtTokenProvider.parse(accessToken))
        .isPresent()
        .get()
        .satisfies(claims -> assertThat(claims).containsEntry("roles", List.of("ADMIN")));
  }
}
