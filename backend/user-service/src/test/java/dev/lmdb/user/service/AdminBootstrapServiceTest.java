package dev.lmdb.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.lmdb.user.model.Role;
import dev.lmdb.user.model.User;
import dev.lmdb.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link AdminBootstrapService}: every branch that decides whether to provision the
 * ADMIN account, without spinning up Spring Boot. The repository is mocked; a real (low-strength)
 * BCrypt encoder is used so the stored hash is genuinely verifiable, matching {@link
 * AuthServiceTest}'s style.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminBootstrapService Unit Tests")
class AdminBootstrapServiceTest {

  @Mock private UserRepository userRepository;

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

  private AdminBootstrapService adminBootstrapService;

  /** Wires the service with empty bootstrap config by default; each test sets what it needs. */
  @BeforeEach
  void setUp() {
    adminBootstrapService = new AdminBootstrapService(userRepository, passwordEncoder);
  }

  /**
   * The documented default: nothing configured must mean nothing happens. This is what keeps an
   * unconfigured deployment free of a guessable admin, rather than defaulting to one.
   */
  @Test
  @DisplayName("No config at all: no admin is created")
  void skipsWhenNothingConfigured() {
    adminBootstrapService.bootstrapIfConfigured();

    verify(userRepository, never()).save(any(User.class));
  }

  /**
   * A partially-filled config (e.g. username set but password blank) is far more likely to be a
   * typo than a deliberate request, so it must be treated the same as "nothing configured" — not an
   * attempt to fill in the gaps.
   */
  @Test
  @DisplayName("Partial config (password missing): no admin is created")
  void skipsWhenConfigIsPartial() {
    configure("bootadmin", "bootadmin@example.com", "");

    adminBootstrapService.bootstrapIfConfigured();

    verify(userRepository, never()).save(any(User.class));
  }

  /**
   * The bootstrap account can reach the gateway's security-management API, so a short password must
   * be refused outright rather than merely accepted and hashed.
   */
  @Test
  @DisplayName("Password shorter than the minimum: no admin is created")
  void skipsWhenPasswordTooShort() {
    configure("bootadmin", "bootadmin@example.com", "short-pw");

    adminBootstrapService.bootstrapIfConfigured();

    verify(userRepository, never()).save(any(User.class));
  }

  /**
   * Re-running bootstrap against a deployment that already has the admin account must be a silent
   * no-op — the mechanism is meant to be safe to leave wired in permanently across restarts, not a
   * one-shot script.
   */
  @Test
  @DisplayName("Bootstrap username already an ADMIN: no-op, no duplicate created")
  void skipsWhenAdminAlreadyExists() {
    configure("bootadmin", "bootadmin@example.com", "correct-horse-battery");
    when(userRepository.findByUsername("bootadmin")).thenReturn(Optional.of(adminUser()));

    adminBootstrapService.bootstrapIfConfigured();

    verify(userRepository, never()).save(any(User.class));
  }

  /**
   * A username collision with an ordinary account must never silently promote it to ADMIN — that
   * would grant elevated access to whoever happened to register that name first.
   */
  @Test
  @DisplayName("Bootstrap username taken by a non-admin: left untouched, no admin created")
  void skipsAndDoesNotPromoteExistingNonAdmin() {
    configure("bootadmin", "bootadmin@example.com", "correct-horse-battery");
    User existingUser =
        User.builder()
            .id(UUID.randomUUID())
            .username("bootadmin")
            .email("someone-else@example.com")
            .passwordHash("irrelevant")
            .role(Role.USER)
            .enabled(true)
            .accountNonLocked(true)
            .createdAt(LocalDateTime.now())
            .build();
    when(userRepository.findByUsername("bootadmin")).thenReturn(Optional.of(existingUser));

    adminBootstrapService.bootstrapIfConfigured();

    verify(userRepository, never()).save(any(User.class));
    assertThat(existingUser.getRole()).isEqualTo(Role.USER);
  }

  /**
   * The email uniqueness constraint applies to the bootstrap account too: creating it anyway would
   * either crash startup on the DB constraint or (if the constraint were ever relaxed) create a
   * second account sharing an email with someone else's.
   */
  @Test
  @DisplayName("Bootstrap email already registered to another account: no admin is created")
  void skipsWhenEmailTaken() {
    configure("bootadmin", "taken@example.com", "correct-horse-battery");
    when(userRepository.findByUsername("bootadmin")).thenReturn(Optional.empty());
    when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

    adminBootstrapService.bootstrapIfConfigured();

    verify(userRepository, never()).save(any(User.class));
  }

  /**
   * The happy path: fully configured, no collisions — the account must be created with the ADMIN
   * role and a BCrypt hash that actually verifies against the configured raw password (proving the
   * raw value isn't what gets persisted).
   */
  @Test
  @DisplayName("Fully configured, no collisions: ADMIN account is created with a hashed password")
  void createsAdminWhenFullyConfiguredAndUnclaimed() {
    configure("bootadmin", "bootadmin@example.com", "correct-horse-battery");
    when(userRepository.findByUsername("bootadmin")).thenReturn(Optional.empty());
    when(userRepository.existsByEmail("bootadmin@example.com")).thenReturn(false);
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    adminBootstrapService.bootstrapIfConfigured();

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();

    assertThat(saved.getUsername()).isEqualTo("bootadmin");
    assertThat(saved.getEmail()).isEqualTo("bootadmin@example.com");
    assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
    assertThat(saved.isEnabled()).isTrue();
    assertThat(saved.isAccountNonLocked()).isTrue();
    assertThat(saved.getPasswordHash()).isNotEqualTo("correct-horse-battery");
    assertThat(passwordEncoder.matches("correct-horse-battery", saved.getPasswordHash())).isTrue();
  }

  /**
   * Sets the three {@code @Value}-injected bootstrap properties directly, the way Spring would at
   * startup — avoids standing up a full application context just to test property binding.
   *
   * @param username bootstrap username, or blank/empty to simulate "unset"
   * @param email bootstrap email
   * @param password bootstrap password
   */
  private void configure(String username, String email, String password) {
    ReflectionTestUtils.setField(adminBootstrapService, "bootstrapUsername", username);
    ReflectionTestUtils.setField(adminBootstrapService, "bootstrapEmail", email);
    ReflectionTestUtils.setField(adminBootstrapService, "bootstrapPassword", password);
  }

  /**
   * Builds an existing ADMIN account, as if a previous run had already bootstrapped it.
   *
   * @return an enabled, unlocked ADMIN account
   */
  private User adminUser() {
    return User.builder()
        .id(UUID.randomUUID())
        .username("bootadmin")
        .email("bootadmin@example.com")
        .passwordHash(passwordEncoder.encode("correct-horse-battery"))
        .role(Role.ADMIN)
        .enabled(true)
        .accountNonLocked(true)
        .createdAt(LocalDateTime.now())
        .build();
  }
}
