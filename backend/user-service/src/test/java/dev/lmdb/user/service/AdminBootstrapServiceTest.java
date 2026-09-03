package dev.lmdb.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import org.springframework.dao.DataIntegrityViolationException;
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

  /**
   * Real encoder (strength 4 for test speed — behavior identical), matching {@link
   * AuthServiceTest}.
   */
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
   * A partially-filled config (username and email set, password blank) is far more likely to be a
   * typo than a deliberate request, so it must be treated the same as "nothing configured" — not an
   * attempt to fill in the gaps.
   */
  @Test
  @DisplayName("Partial config (password missing): no admin is created")
  void skipsWhenPasswordMissing() {
    configure("bootadmin", "bootadmin@example.com", "");

    adminBootstrapService.bootstrapIfConfigured();

    verify(userRepository, never()).save(any(User.class));
  }

  /**
   * Same guard, the other two fields: a blank username with email/password set must be refused
   * exactly like the all-blank case, not treated as "two thirds configured".
   */
  @Test
  @DisplayName("Partial config (username missing): no admin is created")
  void skipsWhenUsernameMissing() {
    configure("   ", "bootadmin@example.com", "correct-horse-battery");

    adminBootstrapService.bootstrapIfConfigured();

    verify(userRepository, never()).save(any(User.class));
  }

  /**
   * Same guard, the email field: covers the one combination the other partial-config tests don't
   * (username and password present, email blank) so a bug in any single one of the three {@code
   * hasText} checks would fail at least one test.
   */
  @Test
  @DisplayName("Partial config (email missing): no admin is created")
  void skipsWhenEmailMissing() {
    configure("bootadmin", "", "correct-horse-battery");

    adminBootstrapService.bootstrapIfConfigured();

    verify(userRepository, never()).save(any(User.class));
  }

  /**
   * A username longer than the {@code users.username VARCHAR(50)} column would otherwise reach
   * {@code save()} and fail as a raw {@link DataIntegrityViolationException} instead of this clear,
   * actionable refusal.
   */
  @Test
  @DisplayName("Username longer than the column allows: no admin is created")
  void skipsWhenUsernameTooLong() {
    configure("a".repeat(51), "bootadmin@example.com", "correct-horse-battery");

    adminBootstrapService.bootstrapIfConfigured();

    verify(userRepository, never()).save(any(User.class));
  }

  /**
   * A malformed email must be refused the same way {@code RegisterRequest}'s {@code @Email}
   * constraint would refuse it on the public endpoint — this bootstrap path bypasses bean
   * validation entirely, so the check has to be re-done here by hand.
   */
  @Test
  @DisplayName("Malformed email: no admin is created")
  void skipsWhenEmailMalformed() {
    configure("bootadmin", "not-an-email", "correct-horse-battery");

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
   * Exact-boundary coverage for {@link AdminBootstrapService#MIN_PASSWORD_LENGTH}: one character
   * under the floor must still be refused — an off-by-one here (e.g. {@code <=} instead of {@code
   * <}) would silently accept a weaker password than documented.
   */
  @Test
  @DisplayName("Password exactly one character under the minimum: no admin is created")
  void skipsWhenPasswordOneCharUnderMinimum() {
    configure(
        "bootadmin",
        "bootadmin@example.com",
        "1".repeat(AdminBootstrapService.MIN_PASSWORD_LENGTH - 1));

    adminBootstrapService.bootstrapIfConfigured();

    verify(userRepository, never()).save(any(User.class));
  }

  /**
   * The other half of the boundary: a password of exactly the minimum length must be accepted, not
   * refused — otherwise the documented floor would be a lie by one character.
   */
  @Test
  @DisplayName("Password exactly at the minimum length: admin is created")
  void createsAdminWhenPasswordExactlyAtMinimum() {
    String exactLengthPassword = "1".repeat(AdminBootstrapService.MIN_PASSWORD_LENGTH);
    configure("bootadmin", "bootadmin@example.com", exactLengthPassword);
    when(userRepository.findByUsername("bootadmin")).thenReturn(Optional.empty());
    when(userRepository.existsByEmail("bootadmin@example.com")).thenReturn(false);
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    adminBootstrapService.bootstrapIfConfigured();

    verify(userRepository).save(any(User.class));
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
   * The check-then-insert above the {@code save()} call isn't atomic across instances: two replicas
   * starting concurrently could both pass the collision checks and race on the unique constraint.
   * The loser must swallow that as a clean skip, not let it escape — an uncaught exception here
   * would propagate out of {@code AdminBootstrapRunner} (an {@code ApplicationRunner}) and fail
   * that instance's entire startup over a race this service is supposed to tolerate.
   */
  @Test
  @DisplayName("Save races with a concurrent instance: exception is swallowed, not propagated")
  void toleratesConcurrentUniqueConstraintViolation() {
    configure("bootadmin", "bootadmin@example.com", "correct-horse-battery");
    when(userRepository.findByUsername("bootadmin")).thenReturn(Optional.empty());
    when(userRepository.existsByEmail("bootadmin@example.com")).thenReturn(false);
    when(userRepository.save(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key"));

    assertThatCode(() -> adminBootstrapService.bootstrapIfConfigured()).doesNotThrowAnyException();
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
