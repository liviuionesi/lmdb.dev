package dev.lmdb.user.model;

/**
 * User roles for role-based access control.
 *
 * <p>Serialized into JWT access tokens as the {@code roles} claim (a list, matching what the API
 * gateway's {@code JwtUtil#extractRoles} expects) and mapped to Spring Security authorities with
 * the {@code ROLE_} prefix.
 */
public enum Role {

  /** Standard registered user: owns a profile, favorites and a watchlist. */
  USER,

  /**
   * Administrative user. Required by the gateway's {@code /admin/**} security-management API
   * (#237). Registration always issues {@link #USER}, so an ADMIN account has to be provisioned
   * deliberately — see #238.
   */
  ADMIN
}
