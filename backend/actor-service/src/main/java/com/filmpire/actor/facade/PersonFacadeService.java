package com.filmpire.actor.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Read-through / save-through engine for TMDB person data
 * (ARCHITECTURE.md §5.1 rows 8–9, ADR-003):
 *
 * <pre>
 * Request → Redis (@Cacheable, short TTL)
 *             └─ miss → PostgreSQL ({@link TmdbPersonDocument}, freshness-checked)
 *                         └─ miss/stale → real TMDB → save → return
 * </pre>
 *
 * <p>Person details are near-immutable, so one long freshness window
 * ({@code tmdb.facade.detail-refresh}) applies. If TMDB is unreachable and
 * a stale copy exists, the stale copy is served — same resilience contract
 * as movie-service's facade (failure-mode matrix §2.4).</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PersonFacadeService {

    private final TmdbPersonDocumentRepository repository;
    private final TmdbRawClient rawClient;

    /** Freshness window for person documents (near-immutable → long). */
    @Value("${tmdb.facade.detail-refresh:720h}")
    private Duration detailRefresh;

    /**
     * Serves a person-related TMDB path with read-through caching.
     * Redis-cached on the canonical key; repeated requests within the TTL
     * touch neither PostgreSQL nor TMDB.
     *
     * @param path   TMDB path without leading slash (e.g. {@code person/819})
     * @param params query params, {@code api_key} already stripped
     * @return raw TMDB-shaped JSON
     * @throws TmdbUpstreamException  when TMDB answers with an error status
     *                                (passthrough — TMDB explicitly rejected)
     * @throws ResourceAccessException when TMDB is unreachable and no local
     *                                 copy exists to fall back on
     */
    @Cacheable(value = "tmdbPerson", key = "#root.target.canonicalKey(#path, #params)")
    @Transactional
    public String getRaw(String path, MultiValueMap<String, String> params) {
        String cacheKey = canonicalKey(path, params);

        // 1. PostgreSQL lookup by canonical key.
        Optional<TmdbPersonDocument> stored = repository.findById(cacheKey);

        // 2. Fresh copy → serve without touching TMDB.
        if (stored.isPresent() && isFresh(stored.get())) {
            log.debug("Person facade: serving '{}' from PostgreSQL", cacheKey);
            return stored.get().getJson();
        }

        // 3. Miss or stale → fetch from the real TMDB.
        try {
            String body = rawClient.fetch(path, params);

            // 4. Save-through: upsert the verbatim body under the same key.
            repository.save(TmdbPersonDocument.builder()
                .id(cacheKey)
                .json(body)
                .fetchedAt(Instant.now())
                .build());

            log.info("Person facade: fetched and saved '{}'", cacheKey);
            return body;
        } catch (ResourceAccessException e) {
            // 5. Network failure: stale beats error for read-only data.
            if (stored.isPresent()) {
                log.warn("TMDB unreachable, serving STALE copy of '{}': {}", cacheKey, e.getMessage());
                return stored.get().getJson();
            }
            throw e;
        }
    }

    /**
     * Checks whether a stored document is inside the freshness window.
     *
     * @param doc stored raw document
     * @return {@code true} if it may be served without refreshing
     */
    private boolean isFresh(TmdbPersonDocument doc) {
        // Defensive: rows from older versions without fetchedAt refresh once.
        return doc.getFetchedAt() != null
            && doc.getFetchedAt().plus(detailRefresh).isAfter(Instant.now());
    }

    /**
     * Builds the canonical request key: path plus query params sorted by
     * name — identical algorithm to movie-service so cache keys stay
     * predictable platform-wide. Public because the {@code @Cacheable} SpEL
     * expression invokes it.
     *
     * @param path   TMDB path without leading slash
     * @param params query params, {@code api_key} already stripped
     * @return canonical key, e.g. {@code person/819}
     */
    public String canonicalKey(String path, MultiValueMap<String, String> params) {
        if (params == null || params.isEmpty()) {
            return path;
        }
        TreeMap<String, java.util.List<String>> sorted = new TreeMap<>(params);
        StringBuilder key = new StringBuilder(path).append('?');
        sorted.forEach((name, values) ->
            values.forEach(value -> key.append(name).append('=').append(value).append('&')));
        key.setLength(key.length() - 1); // drop trailing '&'
        return key.toString();
    }
}
