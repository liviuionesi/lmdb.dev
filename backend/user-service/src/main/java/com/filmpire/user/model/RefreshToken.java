package com.filmpire.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A server-side refresh-token record enabling revocation.
 *
 * <p>Refresh tokens are OPAQUE random values (not JWTs): the client holds
 * the raw value, the database stores only its SHA-256 hash — a database leak
 * therefore does not leak usable tokens. Each use rotates the token (old row
 * deleted, new one issued), and logout deletes all of a user's rows, which
 * is what makes logout actually mean something in a stateless-JWT system:
 * access tokens expire quickly (1 h) and cannot be renewed afterwards.</p>
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    /** Surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owner of the token. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 hash (hex) of the opaque token value — never the raw token. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Hard expiry; expired rows are rejected and purged lazily. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** When the token was issued (diagnostics / future rate policies). */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
