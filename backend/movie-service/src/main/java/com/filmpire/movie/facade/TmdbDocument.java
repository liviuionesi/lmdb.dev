package com.filmpire.movie.facade;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A raw TMDB API response persisted verbatim in MongoDB.
 *
 * <p>This is the save-through storage backing the TMDB v3 facade
 * (ARCHITECTURE.md §5.1). The response body is stored as the original JSON
 * string — deliberately NOT parsed into typed entities — because the facade
 * must return responses that are shape-identical to TMDB's. Any
 * parse/re-serialize round-trip could reorder fields or alter number
 * formatting; storing the string guarantees byte-for-byte fidelity.</p>
 *
 * <p>The document id is the canonical request key (path plus sorted query
 * parameters, e.g. {@code movie/popular?page=1}), so each distinct TMDB
 * request caches independently and a repeated request is a primary-key
 * lookup.</p>
 */
@Document(collection = "tmdb_raw_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TmdbDocument {

    /** Canonical request key: {@code <path>?<sorted query params>}. */
    @Id
    private String id;

    /** The exact JSON body TMDB returned for this request. */
    private String json;

    /**
     * When the body was fetched from TMDB. Compared against the per-endpoint
     * staleness window in {@link TmdbFacadeService} to decide whether the
     * stored copy may be served or must be refreshed.
     */
    private Instant fetchedAt;
}
