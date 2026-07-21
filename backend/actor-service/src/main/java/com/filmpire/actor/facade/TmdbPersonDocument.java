package com.filmpire.actor.facade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A raw TMDB person-related response persisted verbatim in PostgreSQL.
 *
 * <p>PostgreSQL equivalent of movie-service's Mongo-backed raw store
 * (ADR-002 assigns actor data to PostgreSQL; ADR-003 mandates byte-fidelity
 * storage). The body lives in a {@code TEXT} column deliberately — NOT
 * {@code JSONB}, because JSONB normalizes key order, whitespace and number
 * representation, which would break the byte-for-byte facade contract.</p>
 */
@Entity
@Table(name = "tmdb_person_documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TmdbPersonDocument {

    /** Canonical request key: {@code <path>?<sorted query params>}. */
    @Id
    @Column(name = "id", length = 512)
    private String id;

    /** The exact JSON body TMDB returned for this request (TEXT, not JSONB). */
    @Column(name = "json", nullable = false, columnDefinition = "text")
    private String json;

    /**
     * When the body was fetched from TMDB; compared against the freshness
     * window in {@link PersonFacadeService} to decide refresh.
     */
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
