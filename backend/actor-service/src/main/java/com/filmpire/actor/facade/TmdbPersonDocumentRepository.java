package com.filmpire.actor.facade;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for {@link TmdbPersonDocument} raw TMDB responses.
 *
 * <p>Primary-key access only — the facade builds a canonical request key
 * and looks it up directly.</p>
 */
public interface TmdbPersonDocumentRepository extends JpaRepository<TmdbPersonDocument, String> {
}
