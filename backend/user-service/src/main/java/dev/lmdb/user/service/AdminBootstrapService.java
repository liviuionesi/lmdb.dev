package dev.lmdb.user.service;

import dev.lmdb.user.dto.AuthDtos;
import dev.lmdb.user.model.Role;
import dev.lmdb.user.model.User;
import dev.lmdb.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Provisions the single ADMIN account a fresh deployment needs to call the gateway's {@code
 * /admin/**} API (issue #238; the gateway-side role rule itself is #237).
 *
 * <p>Registration ({@link AuthService#register}) always issues {@link Role#USER} and there is no
 * promotion endpoint, so without this an ADMIN account can only be minted by hand-editing the
 * database. This service is driven from {@code ADMIN_BOOTSTRAP_USERNAME}/{@code
 * ADMIN_BOOTSTRAP_EMAIL}/{@code ADMIN_BOOTSTRAP_PASSWORD} at startup: all three must be set, or
 * nothing happens — there is no default credential, so an unconfigured deployment simply has no
 * admin rather than a guessable one.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminBootstrapService {

  /**
   * Floor for the bootstrap password, stricter than the 8-char public registration minimum — this
   * account can reach the gateway's security-management API, so a short/guessable value is refused
   * outright rather than merely discouraged.
   */
  static final int MIN_PASSWORD_LENGTH = 12;

  /** Matches {@code users.username}'s {@code @Size(min = 3, max = 50)} ({@link AuthDtos}). */
  static final int MIN_USERNAME_LENGTH = 3;

  static final int MAX_USERNAME_LENGTH = 50;

  /** Matches {@code users.email VARCHAR(255)} ({@code V1__init_user_schema.sql}). */
  static final int MAX_EMAIL_LENGTH = 255;

  /** Same shape as {@code RegisterRequest}'s {@code @Email} constraint, applied by hand here. */
  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${admin.bootstrap.username:}")
  private String bootstrapUsername;

  @Value("${admin.bootstrap.email:}")
  private String bootstrapEmail;

  @Value("${admin.bootstrap.password:}")
  private String bootstrapPassword;

  /**
   * Creates the configured ADMIN account if it doesn't exist yet, or does nothing.
   *
   * <p>Called once at startup by {@code AdminBootstrapRunner}, but written to be safe to call on
   * every restart of an already-provisioned deployment: it only ever inserts, and every path that
   * isn't a clean "create" is a no-op plus a log line explaining why, never a silent overwrite.
   */
  @Transactional
  public void bootstrapIfConfigured() {
    // 1. All three or nothing — a partial config is almost certainly a typo,
    //    not an operator asking for a differently-shaped admin.
    if (!StringUtils.hasText(bootstrapUsername)
        || !StringUtils.hasText(bootstrapEmail)
        || !StringUtils.hasText(bootstrapPassword)) {
      log.info(
          "ADMIN bootstrap not configured (set ADMIN_BOOTSTRAP_USERNAME, "
              + "ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD) — no admin account created.");
      return;
    }

    // 2. Bounds/format, matching RegisterRequest's constraints on the same
    //    columns — without this, an oversized or malformed value reaches
    //    save() below and fails as a raw DataIntegrityViolationException,
    //    which is caught (see step 5) but gives a far less actionable log
    //    than refusing here with the specific reason.
    if (bootstrapUsername.length() < MIN_USERNAME_LENGTH
        || bootstrapUsername.length() > MAX_USERNAME_LENGTH) {
      log.warn(
          "ADMIN_BOOTSTRAP_USERNAME must be between {} and {} characters — refusing to "
              + "provision an admin account.",
          MIN_USERNAME_LENGTH,
          MAX_USERNAME_LENGTH);
      return;
    }
    if (bootstrapEmail.length() > MAX_EMAIL_LENGTH
        || !EMAIL_PATTERN.matcher(bootstrapEmail).matches()) {
      log.warn(
          "ADMIN_BOOTSTRAP_EMAIL is not a valid address (max {} characters) — refusing to "
              + "provision an admin account.",
          MAX_EMAIL_LENGTH);
      return;
    }

    // 3. Refuse a weak credential outright rather than provisioning an
    //    administrator account that's trivial to brute-force.
    if (bootstrapPassword.length() < MIN_PASSWORD_LENGTH) {
      log.warn(
          "ADMIN_BOOTSTRAP_PASSWORD is shorter than {} characters — refusing to provision an "
              + "admin account with a weak credential.",
          MIN_PASSWORD_LENGTH);
      return;
    }

    // 4. Idempotent: a username collision means either this deployment was
    //    already bootstrapped (fine, skip) or the name is taken by an
    //    unrelated USER account (do not silently promote it).
    Optional<User> existing = userRepository.findByUsername(bootstrapUsername);
    if (existing.isPresent()) {
      if (existing.get().getRole() == Role.ADMIN) {
        log.info("ADMIN bootstrap account '{}' already exists — skipping.", bootstrapUsername);
      } else {
        log.warn(
            "ADMIN_BOOTSTRAP_USERNAME '{}' is already registered as a non-admin account — "
                + "leaving it untouched. Choose a different bootstrap username to provision one.",
            bootstrapUsername);
      }
      return;
    }
    if (userRepository.existsByEmail(bootstrapEmail)) {
      log.warn(
          "ADMIN_BOOTSTRAP_EMAIL '{}' is already registered to another account — refusing to "
              + "create a duplicate.",
          bootstrapEmail);
      return;
    }

    // 5. Clear to create: same shape as a normal registration, but with the
    //    ADMIN role a fresh deployment otherwise has no way to grant. The
    //    catch below is the safety net for a race this check-then-insert
    //    can't close on its own (two instances starting concurrently against
    //    the same database): without it, the loser's unique-constraint
    //    violation would escape AdminBootstrapRunner uncaught and fail that
    //    instance's entire startup instead of just skipping bootstrap.
    try {
      User admin =
          userRepository.save(
              User.builder()
                  .username(bootstrapUsername)
                  .email(bootstrapEmail)
                  .passwordHash(passwordEncoder.encode(bootstrapPassword))
                  .role(Role.ADMIN)
                  .enabled(true)
                  .accountNonLocked(true)
                  .createdAt(LocalDateTime.now())
                  .build());
      log.info("Provisioned ADMIN account '{}' from bootstrap configuration.", admin.getUsername());
    } catch (DataIntegrityViolationException e) {
      log.warn(
          "Failed to provision ADMIN bootstrap account '{}': another instance likely created it "
              + "concurrently. Skipping.",
          bootstrapUsername,
          e);
    }
  }
}
