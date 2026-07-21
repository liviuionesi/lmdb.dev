package com.filmpire.movie.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Read-through / save-through engine of the TMDB v3 facade.
 *
 * <p>Implements the data flow from ARCHITECTURE.md §5.1:</p>
 * <pre>
 * Request → Redis (via @Cacheable, short TTL)
 *             └─ miss → MongoDB ({@link TmdbDocument}, freshness-checked)
 *                         └─ miss/stale → real TMDB ({@link TmdbRawClient})
 *                                           └─ save to MongoDB → return
 * </pre>
 *
 * <p>Two freshness windows exist because list endpoints (popular, search,
 * discover) change daily while detail documents (a movie, the genre list)
 * are effectively immutable. Both are configurable
 * ({@code tmdb.facade.list-refresh} / {@code tmdb.facade.detail-refresh}).</p>
 *
 * <p>Resilience: if TMDB is unreachable and a stale copy exists in MongoDB,
 * the stale copy is served (an outdated popular-movies page beats an error
 * page). Only when there is no local copy at all does the failure
 * propagate.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TmdbFacadeService {

    private final TmdbDocumentRepository repository;
    private final TmdbRawClient rawClient;

    /** Freshness window for volatile list endpoints (popular, search, discover). */
    @Value("${tmdb.facade.list-refresh:6h}")
    private Duration listRefresh;

    /** Freshness window for near-immutable detail endpoints (movie details, genres). */
    @Value("${tmdb.facade.detail-refresh:720h}")
    private Duration detailRefresh;

    /**
     * Serves a volatile list endpoint (short freshness window).
     *
     * @param path   TMDB path without leading slash (e.g. {@code movie/popular})
     * @param params query params, {@code api_key} already stripped
     * @return raw TMDB-shaped JSON
     */
    public String getList(String path, MultiValueMap<String, String> params) {
        return getRaw(canonicalKey(path, params), path, params, listRefresh);
    }

    /**
     * Serves a near-immutable detail endpoint (long freshness window).
     *
     * @param path   TMDB path without leading slash (e.g. {@code movie/550})
     * @param params query params, {@code api_key} already stripped
     * @return raw TMDB-shaped JSON
     */
    public String getDetail(String path, MultiValueMap<String, String> params) {
        return getRaw(canonicalKey(path, params), path, params, detailRefresh);
    }

    /**
     * Core read-through lookup. Redis-cached on the canonical key: repeated
     * requests within the Redis TTL never reach MongoDB or TMDB at all.
     *
     * @param cacheKey  canonical request key (also the MongoDB document id)
     * @param path      TMDB path to fetch on miss
     * @param params    query params to forward on miss
     * @param staleness how old a stored copy may be before it is refreshed
     * @return raw TMDB-shaped JSON
     * @throws TmdbUpstreamException  when TMDB answers with an error status
     *                                (passthrough — no stale fallback, because
     *                                TMDB explicitly rejected the request)
     * @throws ResourceAccessException when TMDB is unreachable and no local
     *                                 copy exists to fall back on
     */
    @Cacheable(value = "tmdbFacade", key = "#cacheKey")
    public String getRaw(String cacheKey, String path, MultiValueMap<String, String> params, Duration staleness) {
        // 1. MongoDB lookup by canonical key.
        Optional<TmdbDocument> stored = repository.findById(cacheKey);

        // 2. Fresh copy → serve it without touching TMDB.
        if (stored.isPresent() && isFresh(stored.get(), staleness)) {
            log.debug("TMDB facade: serving '{}' from MongoDB", cacheKey);
            return stored.get().getJson();
        }

        // 3. Miss or stale → fetch from the real TMDB.
        try {
            String body = rawClient.fetch(path, params);

            // 4. Save-through: overwrite (or create) the stored copy so the
            //    local database grows organically with use.
            repository.save(TmdbDocument.builder()
                .id(cacheKey)
                .json(body)
                .fetchedAt(Instant.now())
                .build());

            log.info("TMDB facade: fetched and saved '{}'", cacheKey);
            return body;
        } catch (ResourceAccessException e) {
            // 5. Network failure: serve the stale copy if we have one —
            //    outdated data beats an error for a read-only catalog.
            if (stored.isPresent()) {
                log.warn("TMDB unreachable, serving STALE copy of '{}': {}", cacheKey, e.getMessage());
                return stored.get().getJson();
            }
            throw e;
        }
    }

    /**
     * Checks whether a stored document is still within its freshness window.
     *
     * @param doc       stored raw document
     * @param staleness allowed age
     * @return {@code true} if the copy may be served without refreshing
     */
    private boolean isFresh(TmdbDocument doc, Duration staleness) {
        // Defensive: documents written by older versions may lack fetchedAt —
        // treat them as stale so they get refreshed exactly once.
        return doc.getFetchedAt() != null
            && doc.getFetchedAt().plus(staleness).isAfter(Instant.now());
    }

    /**
     * Builds the canonical request key: path plus query params sorted by
     * name. Sorting makes the key independent of client param order, so
     * {@code ?page=1&language=en} and {@code ?language=en&page=1} share one
     * cache entry.
     *
     * @param path   TMDB path without leading slash
     * @param params query params, {@code api_key} already stripped
     * @return canonical key, e.g. {@code movie/popular?page=1}
     */
    static String canonicalKey(String path, MultiValueMap<String, String> params) {
        if (params == null || params.isEmpty()) {
            return path;
        }
        // TreeMap sorts by param name; values keep their original order.
        TreeMap<String, java.util.List<String>> sorted = new TreeMap<>(params);
        StringBuilder key = new StringBuilder(path).append('?');
        sorted.forEach((name, values) ->
            values.forEach(value -> key.append(name).append('=').append(value).append('&')));
        key.setLength(key.length() - 1); // drop trailing '&'
        return key.toString();
    }
}
