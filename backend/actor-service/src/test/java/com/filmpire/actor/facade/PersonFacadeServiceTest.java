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

    @Test
    @DisplayName("Fresh PostgreSQL copy is served without calling TMDB")
    void freshCopyServedLocally() {
        when(repository.findById(PATH)).thenReturn(Optional.of(
            TmdbPersonDocument.builder().id(PATH).json(STORED_JSON)
                .fetchedAt(Instant.now().minusSeconds(60)).build()));

        assertThat(service.getRaw(PATH, none)).isEqualTo(STORED_JSON);
        verify(rawClient, never()).fetch(anyString(), any());
    }

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

    @Test
    @DisplayName("TMDB unreachable with no copy: failure propagates")
    void failurePropagatesWithoutCopy() {
        when(repository.findById(PATH)).thenReturn(Optional.empty());
        when(rawClient.fetch(PATH, none))
            .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> service.getRaw(PATH, none))
            .isInstanceOf(ResourceAccessException.class);
    }

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
