package com.filmpire.user.repository;

import com.filmpire.user.model.RefreshToken;
import com.filmpire.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

/**
 * JPA repository for {@link RefreshToken} records.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Resolves a presented token by its SHA-256 hash (the raw value is never
     * stored, so lookup happens on the hash).
     *
     * @param tokenHash hex-encoded SHA-256 of the presented token
     * @return the record, if the token was issued and not yet rotated/revoked
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes ALL of a user's refresh tokens — the logout operation.
     *
     * @param user account whose sessions end
     */
    @Modifying
    void deleteByUser(User user);
}
