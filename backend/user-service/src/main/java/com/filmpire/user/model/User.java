package com.filmpire.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A registered Filmpire account (ARCHITECTURE.md §3.5).
 *
 * <p>Local accounts are independent of the TMDB proxy authentication used by
 * the React app's login flow (ADR/issue #33): this entity backs the native
 * {@code /api/v1} auth and profile endpoints. Passwords are stored as BCrypt
 * hashes only — the raw password never leaves the registration/login request
 * scope.</p>
 *
 * <p>Mutable JPA entity by design (Hibernate requires it); DTOs exposed to
 * clients are immutable records.</p>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** Primary key, application-generated UUID. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Unique login name; also the JWT subject claim. */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** Unique contact address, used for duplicate-account checks. */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt hash of the password (strength 12 — see SecurityConfig). */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** RBAC role, serialized into the JWT {@code roles} claim. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Account switch: disabled users cannot authenticate. */
    @Column(nullable = false)
    private boolean enabled;

    /** Lock switch: locked accounts cannot authenticate (future lockout policy). */
    @Column(name = "account_non_locked", nullable = false)
    private boolean accountNonLocked;

    /** When the account was registered. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Last successful login, updated by AuthService on each login. */
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
}
