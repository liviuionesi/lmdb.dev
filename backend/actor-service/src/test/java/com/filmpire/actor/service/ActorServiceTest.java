package com.filmpire.actor.service;

import com.filmpire.actor.client.TmdbPersonClient;
import com.filmpire.actor.client.dto.TmdbPersonImagesResponse;
import com.filmpire.actor.client.dto.TmdbPersonMovieCreditsResponse;
import com.filmpire.actor.client.dto.TmdbPersonResponse;
import com.filmpire.actor.client.dto.TmdbPersonSearchResponse;
import com.filmpire.actor.dto.ActorDtos.ActorDto;
import com.filmpire.actor.dto.ActorDtos.ActorImageDto;
import com.filmpire.actor.dto.ActorDtos.ActorSearchResponse;
import com.filmpire.actor.dto.ActorDtos.FilmographyEntryDto;
import com.filmpire.actor.dto.ActorDtos.FilmographyPageDto;
import com.filmpire.actor.model.Actor;
import com.filmpire.actor.model.ActorProfileImage;
import com.filmpire.actor.repository.ActorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ActorService}.
 *
 * <p>Exercises the service's logic in isolation with Mockito: both
 * {@link ActorRepository} (PostgreSQL) and {@link TmdbPersonClient} (TMDB HTTP)
 * are mocked, so no Spring context, database or network is involved — the
 * end-to-end wiring is covered separately by
 * {@code ActorServiceIntegrationTest} (Testcontainers + WireMock).</p>
 *
 * <p>The contract under test is ADR-010's read-through/save-through model:
 * PostgreSQL is consulted first and TMDB is only called on a miss, after which
 * the mapped entity is persisted. Complementing that, list endpoints (search,
 * popular) always call TMDB for ranking but upsert every person they return.</p>
 *
 * @see ActorService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActorService Tests")
class ActorServiceTest {

    private static final Long BRAD_PITT_ID = 287L;
    private static final String API_KEY = "test-api-key";

    @Mock
    private TmdbPersonClient tmdbPersonClient;

    @Mock
    private ActorRepository actorRepository;

    @InjectMocks
    private ActorService actorService;

    /**
     * Injects the TMDB API key that Spring would normally bind via
     * {@code @Value}, which no Mockito mock can supply.
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(actorService, "tmdbApiKey", API_KEY);
    }

    /**
     * A persisted actor must be served straight from PostgreSQL. This is the
     * whole point of save-through: once a person is in our catalog, TMDB is no
     * longer in the request path, so the service stays up (and fast) even when
     * TMDB is slow or rate-limiting us.
     */
    @Test
    @DisplayName("getActor: served from PostgreSQL when present, no TMDB call")
    void getActorReadsThroughToPostgres() {
        // Given: the actor is already in our own catalog
        when(actorRepository.findById(BRAD_PITT_ID)).thenReturn(Optional.of(persistedActor()));

        // When
        ActorDto dto = actorService.getActor(BRAD_PITT_ID);

        // Then: mapped from the persisted row, and TMDB was never consulted
        assertThat(dto.tmdbId()).isEqualTo(BRAD_PITT_ID);
        assertThat(dto.name()).isEqualTo("Brad Pitt");
        assertThat(dto.knownForDepartment()).isEqualTo("Acting");
        verifyNoInteractions(tmdbPersonClient);
    }

    /**
     * A cache miss must fetch from TMDB and persist the mapped entity, so the
     * next request for the same person is served locally. Asserting on the
     * saved entity (not just the returned DTO) is what proves the
     * save-through half of the contract actually happened.
     */
    @Test
    @DisplayName("getActor: TMDB fetch on miss, mapped and saved through")
    void getActorFetchesAndSavesOnMiss() {
        // Given: not in PostgreSQL, TMDB has the full profile
        when(actorRepository.findById(BRAD_PITT_ID)).thenReturn(Optional.empty());
        when(tmdbPersonClient.getPersonDetails(BRAD_PITT_ID, API_KEY)).thenReturn(tmdbPerson());
        when(actorRepository.save(any(Actor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ActorDto dto = actorService.getActor(BRAD_PITT_ID);

        // Then: TMDB's fields survive the mapping into our entity
        ArgumentCaptor<Actor> saved = ArgumentCaptor.forClass(Actor.class);
        verify(actorRepository).save(saved.capture());
        assertThat(saved.getValue().getTmdbId()).isEqualTo(BRAD_PITT_ID);
        assertThat(saved.getValue().getImdbId()).isEqualTo("nm0000093");
        assertThat(saved.getValue().getAlsoKnownAs()).contains("William Bradley Pitt");
        assertThat(saved.getValue().getSyncedAt()).isNotNull();
        assertThat(dto.homepage()).isEqualTo("https://bradpitt.com");
    }

    /**
     * TMDB serves a null biography for people it has no write-up for. The
     * entity normalizes that to an empty string so clients never have to
     * null-check a field that is always present in TMDB's own responses.
     */
    @Test
    @DisplayName("getActor: null biography from TMDB is normalized to empty string")
    void getActorNormalizesNullBiography() {
        // Given: TMDB returns a profile with no biography
        TmdbPersonResponse noBio = new TmdbPersonResponse(
            BRAD_PITT_ID, "Brad Pitt", null, null, null, null,
            null, List.of(), null, null, null, null, null);
        when(actorRepository.findById(BRAD_PITT_ID)).thenReturn(Optional.empty());
        when(tmdbPersonClient.getPersonDetails(BRAD_PITT_ID, API_KEY)).thenReturn(noBio);
        when(actorRepository.save(any(Actor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When / Then
        assertThat(actorService.getActor(BRAD_PITT_ID).biography()).isEmpty();
    }

    /**
     * Filmography must come back newest-first, and credits TMDB has no release
     * date for must sink to the end rather than sorting as if they were the
     * oldest — an unreleased film is not a 1900s film.
     */
    @Test
    @DisplayName("getFilmography: sorted newest first, undated credits last")
    void getFilmographySortsNewestFirst() {
        // Given: credits deliberately out of order, including an undated one
        when(tmdbPersonClient.getPersonMovieCredits(BRAD_PITT_ID, API_KEY)).thenReturn(
            new TmdbPersonMovieCreditsResponse(BRAD_PITT_ID, List.of(
                credit(1L, "Older", "1999-01-01"),
                credit(2L, "Undated", null),
                credit(3L, "Newer", "2019-01-01"))));

        // When
        List<FilmographyEntryDto> result = actorService.getFilmography(BRAD_PITT_ID);

        // Then
        assertThat(result).extracting(FilmographyEntryDto::title)
            .containsExactly("Newer", "Older", "Undated");
    }

    /**
     * The native API pages in memory because TMDB's movie_credits has no page
     * parameter. Page 2 of a 3-item list at size 2 must therefore hold exactly
     * the one remaining credit, with the totals describing the whole set.
     */
    @Test
    @DisplayName("getFilmographyPage: slices the full credit list, totals span all pages")
    void getFilmographyPageSlices() {
        // Given: three credits, requested two per page
        when(tmdbPersonClient.getPersonMovieCredits(BRAD_PITT_ID, API_KEY)).thenReturn(
            new TmdbPersonMovieCreditsResponse(BRAD_PITT_ID, List.of(
                credit(1L, "A", "2021-01-01"),
                credit(2L, "B", "2020-01-01"),
                credit(3L, "C", "2019-01-01"))));

        // When: asking for the last (partial) page
        FilmographyPageDto page = actorService.getFilmographyPage(BRAD_PITT_ID, 2, 2);

        // Then
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.results()).extracting(FilmographyEntryDto::title).containsExactly("C");
    }

    /**
     * Paging past the end must yield an empty page, not an exception or an
     * index error — TMDB's own list endpoints behave this way, and the facade
     * contract means clients may probe pages blindly.
     */
    @Test
    @DisplayName("getFilmographyPage: page beyond the end is empty, not an error")
    void getFilmographyPageBeyondEndIsEmpty() {
        // Given
        when(tmdbPersonClient.getPersonMovieCredits(BRAD_PITT_ID, API_KEY)).thenReturn(
            new TmdbPersonMovieCreditsResponse(BRAD_PITT_ID, List.of(credit(1L, "A", "2021-01-01"))));

        // When / Then
        assertThat(actorService.getFilmographyPage(BRAD_PITT_ID, 99, 20).results()).isEmpty();
    }

    /**
     * Images follow the same read-through/save-through rule as the profile:
     * fetched from TMDB once, then persisted onto the actor. Only the CDN
     * reference is stored — never the bytes (ARCHITECTURE.md §3.8).
     */
    @Test
    @DisplayName("getImages: fetched from TMDB on miss and saved onto the actor")
    void getImagesFetchesAndPersists() {
        // Given: the actor exists but has no images stored yet
        Actor actor = persistedActor();
        actor.setProfileImages(new ArrayList<>());
        when(actorRepository.findById(BRAD_PITT_ID)).thenReturn(Optional.of(actor));
        when(tmdbPersonClient.getPersonImages(BRAD_PITT_ID, API_KEY)).thenReturn(
            new TmdbPersonImagesResponse(BRAD_PITT_ID, List.of(
                new TmdbPersonImagesResponse.TmdbProfileImage(
                    "/profile.jpg", 0.667, 2100, 1400, null, 5.3, 7))));
        when(actorRepository.save(any(Actor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        List<ActorImageDto> images = actorService.getImages(BRAD_PITT_ID);

        // Then: mapped out, and persisted back onto the actor
        assertThat(images).singleElement()
            .satisfies(i -> {
                assertThat(i.filePath()).isEqualTo("/profile.jpg");
                assertThat(i.width()).isEqualTo(1400);
                assertThat(i.voteCount()).isEqualTo(7);
            });
        verify(actorRepository).save(any(Actor.class));
    }

    /**
     * Once images are persisted, TMDB must drop out of the request path
     * entirely — the same guarantee the profile itself gets.
     */
    @Test
    @DisplayName("getImages: served from PostgreSQL when already persisted")
    void getImagesServedFromPostgres() {
        // Given: the actor already carries a persisted image
        Actor actor = persistedActor();
        actor.setProfileImages(new ArrayList<>(List.of(ActorProfileImage.builder()
            .filePath("/cached.jpg").width(500).height(750).build())));
        when(actorRepository.findById(BRAD_PITT_ID)).thenReturn(Optional.of(actor));

        // When
        List<ActorImageDto> images = actorService.getImages(BRAD_PITT_ID);

        // Then: no TMDB image call, and nothing re-saved
        assertThat(images).singleElement()
            .extracting(ActorImageDto::filePath).isEqualTo("/cached.jpg");
        verify(tmdbPersonClient, never()).getPersonImages(anyLong(), anyString());
        verify(actorRepository, never()).save(any(Actor.class));
    }

    /**
     * TMDB reports no images for obscure people. An empty profiles array must
     * map to an empty list rather than blowing up, and a null array (which
     * TMDB has been known to emit) must be tolerated too.
     */
    @Test
    @DisplayName("getImages: null profiles array from TMDB yields an empty list")
    void getImagesHandlesNullProfiles() {
        // Given
        Actor actor = persistedActor();
        actor.setProfileImages(new ArrayList<>());
        when(actorRepository.findById(BRAD_PITT_ID)).thenReturn(Optional.of(actor));
        when(tmdbPersonClient.getPersonImages(BRAD_PITT_ID, API_KEY))
            .thenReturn(new TmdbPersonImagesResponse(BRAD_PITT_ID, null));
        when(actorRepository.save(any(Actor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When / Then
        assertThat(actorService.getImages(BRAD_PITT_ID)).isEmpty();
    }

    /**
     * Search always hits TMDB (we don't reimplement its relevance ranking),
     * but every result must be upserted so the catalog grows from real
     * traffic — that is what makes this a clone rather than a proxy.
     */
    @Test
    @DisplayName("search: results upserted into PostgreSQL as stubs")
    void searchUpsertsResults() {
        // Given: TMDB returns two hits, neither known locally
        when(tmdbPersonClient.searchPersons(API_KEY, "pitt", 1)).thenReturn(
            new TmdbPersonSearchResponse(1, 1, 2L, List.of(
                summary(287L, "Brad Pitt"),
                summary(288L, "Michael Pitt"))));
        when(actorRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(actorRepository.save(any(Actor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ActorSearchResponse response = actorService.search("pitt", 1);

        // Then
        assertThat(response.totalResults()).isEqualTo(2);
        assertThat(response.results()).hasSize(2);
        verify(actorRepository, times(2)).save(any(Actor.class));
    }

    /**
     * A list-endpoint hit carries far less than a detail fetch. Upserting one
     * over an already-detailed row must not wipe the richer fields — losing a
     * biography just because someone searched for that actor would be a silent
     * data-loss bug.
     */
    @Test
    @DisplayName("search: upsert refreshes stub fields without clobbering a detailed row")
    void searchUpsertPreservesExistingDetail() {
        // Given: a fully-detailed actor already persisted from a prior detail fetch
        Actor existing = persistedActor();
        when(tmdbPersonClient.searchPersons(API_KEY, "pitt", 1)).thenReturn(
            new TmdbPersonSearchResponse(1, 1, 1L, List.of(summary(BRAD_PITT_ID, "Brad Pitt"))));
        when(actorRepository.findById(BRAD_PITT_ID)).thenReturn(Optional.of(existing));
        when(actorRepository.save(any(Actor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        actorService.search("pitt", 1);

        // Then: biography and birth date survive the stub upsert
        ArgumentCaptor<Actor> saved = ArgumentCaptor.forClass(Actor.class);
        verify(actorRepository).save(saved.capture());
        assertThat(saved.getValue().getBiography()).isEqualTo("An actor.");
        assertThat(saved.getValue().getBirthDate()).isEqualTo(LocalDate.of(1963, 12, 18));
    }

    /**
     * Popular shares search's envelope and upsert path, so it must grow the
     * catalog the same way — this is the cheapest way to seed the database
     * with people users are actually likely to look up.
     */
    @Test
    @DisplayName("getPopular: TMDB ranking passed through, every person upserted")
    void getPopularUpsertsResults() {
        // Given
        when(tmdbPersonClient.getPopularPersons(API_KEY, 1)).thenReturn(
            new TmdbPersonSearchResponse(1, 500, 10000L, List.of(summary(976L, "Jason Statham"))));
        when(actorRepository.findById(976L)).thenReturn(Optional.empty());
        when(actorRepository.save(any(Actor.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ActorSearchResponse response = actorService.getPopular(1);

        // Then
        assertThat(response.totalPages()).isEqualTo(500);
        assertThat(response.results()).singleElement()
            .satisfies(a -> assertThat(a.name()).isEqualTo("Jason Statham"));
        verify(actorRepository).save(any(Actor.class));
    }

    /**
     * TMDB omits the paging fields on some responses. The service must fall
     * back to the requested page and zeroed totals rather than NPE-ing while
     * unboxing them.
     */
    @Test
    @DisplayName("search: absent TMDB paging fields fall back to the requested page")
    void searchHandlesMissingPagingFields() {
        // Given: a response with every paging field null
        when(tmdbPersonClient.searchPersons(API_KEY, "pitt", 3)).thenReturn(
            new TmdbPersonSearchResponse(null, null, null, List.of()));

        // When
        ActorSearchResponse response = actorService.search("pitt", 3);

        // Then
        assertThat(response.page()).isEqualTo(3);
        assertThat(response.totalPages()).isZero();
        assertThat(response.totalResults()).isZero();
    }

    /** @return a fully-populated actor as a prior detail fetch would have stored it */
    private static Actor persistedActor() {
        return Actor.builder()
            .tmdbId(BRAD_PITT_ID)
            .name("Brad Pitt")
            .biography("An actor.")
            .birthDate(LocalDate.of(1963, 12, 18))
            .birthPlace("Shawnee, Oklahoma, USA")
            .profilePath("/profile.jpg")
            .popularity(50.0)
            .alsoKnownAs(new ArrayList<>(List.of("William Bradley Pitt")))
            .knownForDepartment("Acting")
            .gender(2)
            .imdbId("nm0000093")
            .homepage("https://bradpitt.com")
            .adult(false)
            .syncedAt(LocalDateTime.now())
            .build();
    }

    /** @return TMDB's person-detail response for the same actor */
    private static TmdbPersonResponse tmdbPerson() {
        return new TmdbPersonResponse(
            BRAD_PITT_ID, "Brad Pitt", "An actor.", LocalDate.of(1963, 12, 18),
            "Shawnee, Oklahoma, USA", "/profile.jpg", 50.0,
            List.of("William Bradley Pitt"), "Acting", 2, "nm0000093",
            "https://bradpitt.com", false);
    }

    /**
     * @param id          TMDB movie id
     * @param title       movie title
     * @param releaseDate release date, null to model an undated credit
     * @return one cast credit
     */
    private static TmdbPersonMovieCreditsResponse.TmdbCastCredit credit(
            Long id, String title, String releaseDate) {
        return new TmdbPersonMovieCreditsResponse.TmdbCastCredit(
            id, title, "Self", releaseDate, "/poster.jpg", 7.5);
    }

    /**
     * @param id   TMDB person id
     * @param name person's name
     * @return one person-list summary as search/popular return them
     */
    private static TmdbPersonSearchResponse.TmdbPersonSummary summary(Long id, String name) {
        return new TmdbPersonSearchResponse.TmdbPersonSummary(
            id, name, "/profile.jpg", 42.0, "Acting", 2, false);
    }
}
