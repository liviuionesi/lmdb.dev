package com.filmpire.actor.facade;

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
 * Unit tests for {@link PersonFacadeService}: read-through decisions,
 * save-through persistence, stale fallback and canonical keys — the
 * PostgreSQL twin of movie-service's facade tests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PersonFacadeService Unit Tests")
class PersonFacadeServiceTest {

    private static final String PATH = "person/819";
    private static final String STORED_JSON = "{\"id\":819,\"name\":\"Edward Norton\"}";
    private static final String TMDB_JSON = "{\"id\":819,\"name\":\"Edward Norton\",\"popularity\":9.1}";

    @Mock
    private TmdbPersonDocumentRepository repository;

    @Mock
    private TmdbRawClient rawClient;

    @InjectMocks
    private PersonFacadeService service;

    /** Empty params used by most tests. */
    private final MultiValueMap<String, String> none = new LinkedMultiValueMap<>();

    /** Injects the @Value-bound freshness window (no Spring context here). */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "detailRefresh", Duration.ofHours(720));
    }

    /**
     * A document inside its freshness window must be served straight from
     * PostgreSQL — the read-through's whole point is that warm data costs zero
     * TMDB calls, which is what protects the shared 40-req/10s rate budget.
     */
    @Test
    @DisplayName("Fresh PostgreSQL copy is served without calling TMDB")
    void freshCopyServedLocally() {
        when(repository.findById(PATH)).thenReturn(Optional.of(
            TmdbPersonDocument.builder().id(PATH).json(STORED_JSON)
                .fetchedAt(Instant.now().minusSeconds(60)).build()));

        assertThat(service.getRaw(PATH, none)).isEqualTo(STORED_JSON);
        verify(rawClient, never()).fetch(anyString(), any());
    }

    /**
     * A copy older than the freshness window must be refreshed from TMDB and
     * written back under the same key (save-through overwrite), so the cache
     * self-heals toward current data instead of serving indefinitely-stale
     * documents.
     */
    @Test
    @DisplayName("Stale copy triggers refetch and save-through overwrite")
    void staleCopyRefetched() {
        when(repository.findById(PATH)).thenReturn(Optional.of(
            TmdbPersonDocument.builder().id(PATH).json(STORED_JSON)
                .fetchedAt(Instant.now().minus(Duration.ofDays(31))).build()));
        when(rawClient.fetch(PATH, none)).thenReturn(TMDB_JSON);

        assertThat(service.getRaw(PATH, none)).isEqualTo(TMDB_JSON);
        verify(repository).save(any(TmdbPersonDocument.class));
    }

    /**
     * When TMDB is unreachable but a (stale) copy exists, serving the stale
     * data beats returning an error: for a read-only catalog an outdated
     * actor page is far more useful to the user than a 5xx.
     */
    @Test
    @DisplayName("TMDB unreachable: stale copy is served as fallback")
    void staleFallbackOnNetworkFailure() {
        when(repository.findById(PATH)).thenReturn(Optional.of(
            TmdbPersonDocument.builder().id(PATH).json(STORED_JSON)
                .fetchedAt(Instant.now().minus(Duration.ofDays(40))).build()));
        when(rawClient.fetch(PATH, none))
            .thenThrow(new ResourceAccessException("connection refused"));

        assertThat(service.getRaw(PATH, none)).isEqualTo(STORED_JSON);
    }

    /**
     * With neither a live TMDB nor a cached copy there is nothing truthful to
     * return, so the network failure must propagate (to become a 502 at the
     * controller) rather than be masked as an empty or fabricated success.
     */
    @Test
    @DisplayName("TMDB unreachable with no copy: failure propagates")
    void failurePropagatesWithoutCopy() {
        when(repository.findById(PATH)).thenReturn(Optional.empty());
        when(rawClient.fetch(PATH, none))
            .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> service.getRaw(PATH, none))
            .isInstanceOf(ResourceAccessException.class);
    }

    /**
     * The key must sort query params by name so that logically-identical
     * requests with different param order collapse to one cache entry. Keeping
     * the algorithm byte-identical to movie-service's keeps cache keys
     * predictable platform-wide (and reviewable in one place).
     */
    @Test
    @DisplayName("Canonical key sorts params — identical to movie-service's algorithm")
    void canonicalKeyMatchesPlatformConvention() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", "2");
        params.add("query", "norton");

        assertThat(service.canonicalKey("search/person", params))
            .isEqualTo("search/person?page=2&query=norton");
        assertThat(service.canonicalKey(PATH, new LinkedMultiValueMap<>()))
            .isEqualTo(PATH);
    }
}
