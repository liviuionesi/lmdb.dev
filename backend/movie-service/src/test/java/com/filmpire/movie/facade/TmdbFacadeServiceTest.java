package com.filmpire.movie.facade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TmdbFacadeService}: read-through decisions,
 * save-through persistence, stale fallback, and canonical key building.
 * The MongoDB repository and raw TMDB client are mocked — integration
 * behavior is covered by {@code TmdbFacadeIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TmdbFacadeService Unit Tests")
class TmdbFacadeServiceTest {

    private static final String KEY = "movie/popular?page=1";
    private static final String PATH = "movie/popular";
    private static final String FRESH_JSON = "{\"page\":1,\"results\":[]}";
    private static final String TMDB_JSON = "{\"page\":1,\"results\":[{\"id\":550}]}";

    @Mock
    private TmdbDocumentRepository repository;

    @Mock
    private TmdbRawClient rawClient;

    @InjectMocks
    private TmdbFacadeService service;

    /** Query params used by most tests: page=1, api_key already stripped. */
    private MultiValueMap<String, String> params;

    /**
     * Injects the {@code @Value}-bound freshness windows (no Spring context
     * in a unit test) and builds the common params map.
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "listRefresh", Duration.ofHours(6));
        ReflectionTestUtils.setField(service, "detailRefresh", Duration.ofHours(720));
        params = new LinkedMultiValueMap<>();
        params.add("page", "1");
    }

    @Test
    @DisplayName("Fresh MongoDB copy is served without calling TMDB")
    void freshCopyServedWithoutTmdbCall() {
        // Given: a document fetched one minute ago (well within 6h window)
        when(repository.findById(KEY)).thenReturn(Optional.of(
            TmdbDocument.builder().id(KEY).json(FRESH_JSON)
                .fetchedAt(Instant.now().minusSeconds(60)).build()));

        // When
        String body = service.getList(PATH, params);

        // Then: stored copy returned verbatim, TMDB never contacted
        assertThat(body).isEqualTo(FRESH_JSON);
        verify(rawClient, never()).fetch(anyString(), any());
    }

    @Test
    @DisplayName("Stale copy triggers refetch and save-through overwrite")
    void staleCopyRefetchedAndSaved() {
        // Given: a document older than the 6h list window
        when(repository.findById(KEY)).thenReturn(Optional.of(
            TmdbDocument.builder().id(KEY).json(FRESH_JSON)
                .fetchedAt(Instant.now().minus(Duration.ofHours(7))).build()));
        when(rawClient.fetch(PATH, params)).thenReturn(TMDB_JSON);

        // When
        String body = service.getList(PATH, params);

        // Then: fresh TMDB body returned and persisted under the same key
        assertThat(body).isEqualTo(TMDB_JSON);
        verify(repository).save(any(TmdbDocument.class));
    }

    @Test
    @DisplayName("Cache miss fetches from TMDB and saves through")
    void missFetchesAndSaves() {
        when(repository.findById(KEY)).thenReturn(Optional.empty());
        when(rawClient.fetch(PATH, params)).thenReturn(TMDB_JSON);

        String body = service.getList(PATH, params);

        assertThat(body).isEqualTo(TMDB_JSON);
        verify(repository).save(any(TmdbDocument.class));
    }

    @Test
    @DisplayName("TMDB unreachable: stale copy is served as fallback")
    void staleFallbackWhenTmdbUnreachable() {
        // Given: stale copy exists AND the network call fails
        when(repository.findById(KEY)).thenReturn(Optional.of(
            TmdbDocument.builder().id(KEY).json(FRESH_JSON)
                .fetchedAt(Instant.now().minus(Duration.ofDays(2))).build()));
        when(rawClient.fetch(PATH, params))
            .thenThrow(new ResourceAccessException("connection refused"));

        // When / Then: stale beats error
        assertThat(service.getList(PATH, params)).isEqualTo(FRESH_JSON);
    }

    @Test
    @DisplayName("TMDB unreachable with no local copy: failure propagates")
    void failurePropagatesWithoutLocalCopy() {
        when(repository.findById(KEY)).thenReturn(Optional.empty());
        when(rawClient.fetch(PATH, params))
            .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> service.getList(PATH, params))
            .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    @DisplayName("Upstream TMDB errors pass through without stale fallback")
    void upstreamErrorPassesThrough() {
        // TMDB explicitly rejected the request (e.g. 404) — the contract says
        // replay TMDB's answer, NOT hide it behind an old cached copy.
        when(repository.findById(KEY)).thenReturn(Optional.empty());
        when(rawClient.fetch(PATH, params))
            .thenThrow(new TmdbUpstreamException(404, "{\"status_code\":34}"));

        assertThatThrownBy(() -> service.getList(PATH, params))
            .isInstanceOf(TmdbUpstreamException.class);
    }

    @Test
    @DisplayName("Canonical key is independent of query parameter order")
    void canonicalKeyIsOrderIndependent() {
        MultiValueMap<String, String> ab = new LinkedMultiValueMap<>();
        ab.add("language", "en");
        ab.add("page", "2");

        MultiValueMap<String, String> ba = new LinkedMultiValueMap<>();
        ba.add("page", "2");
        ba.add("language", "en");

        assertThat(TmdbFacadeService.canonicalKey(PATH, ab))
            .isEqualTo(TmdbFacadeService.canonicalKey(PATH, ba))
            .isEqualTo("movie/popular?language=en&page=2");
    }

    @Test
    @DisplayName("Canonical key without params is just the path")
    void canonicalKeyWithoutParams() {
        assertThat(TmdbFacadeService.canonicalKey("genre/movie/list", new LinkedMultiValueMap<>()))
            .isEqualTo("genre/movie/list");
    }
}
