package com.filmpire.user.model;

/**
 * User roles for role-based access control.
 *
 * <p>Serialized into JWT access tokens as the {@code roles} claim (a list,
 * matching what the API gateway's {@code JwtUtil#extractRoles} expects) and
 * mapped to Spring Security authorities with the {@code ROLE_} prefix.</p>
 */
public enum Role {

    /** Standard registered user: owns a profile, favorites and a watchlist. */
    USER,

    /** Administrative user: reserved for future management endpoints. */
    ADMIN
}
