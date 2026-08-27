package dev.lmdb.ai.security;

import dev.lmdb.shared.exception.UnauthorizedException;
import java.util.UUID;

/**
 * Resolves the caller's identity for ai-service's user-scoped endpoints.
 *
 * <p>The identity is taken from the {@code X-User-Id} header, which is written by api-gateway's
 * {@code JwtAuthenticationFilter} <em>after</em> it has validated the caller's JWT, and which that
 * same filter strips from every inbound request so a client can never supply it itself. That
 * gateway-side strip is what makes this header trustworthy here — without it a caller could assert
 * any identity, and the per-row ownership checks downstream (for example {@link
 * dev.lmdb.ai.repository.ConversationRepository#findByIdAndUserId}) would be checking an
 * attacker-chosen value against itself.
 *
 * <p>Consequently ai-service must never read a user id out of a request body or query parameter for
 * authorization purposes.
 */
public final class CallerIdentity {

  /** Header api-gateway injects with the authenticated caller's user id. */
  public static final String USER_ID_HEADER = "X-User-Id";

  /** Static-only utility. */
  private CallerIdentity() {}

  /**
   * Extracts the authenticated caller's user id from the gateway-issued header value.
   *
   * @param headerValue the raw {@code X-User-Id} header, or {@code null} when absent
   * @return the authenticated caller's user id
   * @throws UnauthorizedException if the header is absent, blank, or not a UUID — all reported
   *     identically so the response never tells a caller which of the three it was
   */
  public static UUID require(String headerValue) {
    if (headerValue == null || headerValue.isBlank()) {
      throw new UnauthorizedException("Authentication required");
    }
    try {
      return UUID.fromString(headerValue);
    } catch (IllegalArgumentException e) {
      throw new UnauthorizedException("Authentication required", e);
    }
  }
}
