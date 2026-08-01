package com.filmpire.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A user's embedded taste vector, backing the semantic-search / "sensible
 * neighbours" feature (#36, ARCHITECTURE.md §3.7, ADR-012). One row per
 * user — {@link #userId} is both the primary key and, like every other
 * cross-service reference in this system, a plain column with no foreign
 * key (ADR-002).
 */
@Entity
@Table(name = "user_taste_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTasteProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    /** pgvector column; dimension must match the embedding model in use (nomic-embed-text, 768). */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 768)
    @Column(columnDefinition = "vector(768)")
    private float[] embedding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Double> featureWeights;

    private Instant lastUpdated;
}
