package dev.lmdb.user.config;

import dev.lmdb.user.service.AdminBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Startup hook that runs the ADMIN bootstrap check (issue #238) once the application context is
 * ready, so a configured admin account exists before the first request is served.
 *
 * <p>Kept thin on purpose: all provisioning logic and its safety checks live in {@link
 * AdminBootstrapService}, which is unit-testable without spinning up Spring Boot.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

  private final AdminBootstrapService adminBootstrapService;

  /**
   * Delegates to {@link AdminBootstrapService#bootstrapIfConfigured()}.
   *
   * @param args unused — bootstrap is driven entirely by {@code admin.bootstrap.*} properties
   */
  @Override
  public void run(ApplicationArguments args) {
    adminBootstrapService.bootstrapIfConfigured();
  }
}
