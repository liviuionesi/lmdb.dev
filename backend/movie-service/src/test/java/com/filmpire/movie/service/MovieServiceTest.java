package com.filmpire.movie.service;

import com.filmpire.movie.client.TmdbClient;
import com.filmpire.movie.client.dto.*;
import com.filmpire.movie.dto.*;
import com.filmpire.movie.mapper.MovieMapper;
import com.filmpire.movie.model.Credits;
import com.filmpire.movie.model.Genre;
import com.filmpire.movie.model.Movie;
import com.filmpire.movie.model.SpokenLanguage;
import com.filmpire.movie.model.Video;
import com.filmpire.movie.repository.MovieRepository;
import com.filmpire.shared.dto.PageResponse;
import com.mongodb.client.result.DeleteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MovieService}.
 * <p>
 * Exercises the service's business logic in isolation with Mockito: {@code MovieRepository}
 * (MongoDB access), {@code TmdbClient} (TMDB HTTP client) and {@code MovieMapper} are all mocked,
 * so no Spring context, database or network is involved. The TMDB API key normally bound via
 * {@code @Value} is injected reflectively in {@link #setUp()}.
 * <p>
 * The central contract under test is the read-through pattern: MongoDB is consulted first and
 * TMDB is only called on a cache miss, after which the result is persisted. Interaction-count
 * details of that pattern are covered separately in {@code MovieServiceCacheTest}.
 *
 * @see MovieService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MovieService Tests")
class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private TmdbClient tmdbClient;

    @Mock
    private MovieMapper movieMapper;

    /** Only used by the schema-drift recovery path (issue #46). */
    @Mock
    private MongoTemplate mongoTemplate;

    /**
     * Supplies the service's own proxy so internal calls to {@code @Cacheable}
     * methods go through Spring (issue #20 / java:S6809). In a unit test there
     * is no proxy, so it is stubbed in {@link #setUp()} to hand back the real
     * instance — preserving the previous direct-call behaviour here while the
     * production path gets genuine cache interception.
     */
    @Mock
    private ObjectProvider<MovieService> selfProvider;

    @InjectMocks
    private MovieService movieService;

    private final String tmdbApiKey = "test-api-key";

    /** Injects the @Value-bound TMDB key (no Spring context in a unit test). */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(movieService, "tmdbApiKey", tmdbApiKey);
        // No Spring proxy in a unit test: hand back the real instance so
        // internal self().xRaw(..) calls behave as direct calls here.
        lenient().when(selfProvider.getObject()).thenReturn(movieService);
    }

    /**
     * The read-through hit path: an existing movie must be returned from
     * MongoDB and mapped, with TMDB never consulted — the money-saving case
     * that keeps the service under TMDB's rate limit.
     */
    @Test
    @DisplayName("getMovieById - Should return from MongoDB when exists")
    void getMovieById_WhenExistsInMongoDB_ShouldReturnFromDB() {
        // Arrange
        Long tmdbId = 550L;
        Movie movie = createTestMovie(tmdbId);
        MovieDto movieDto = createTestMovieDto(tmdbId);

        when(movieRepository.findByTmdbId(tmdbId)).thenReturn(Optional.of(movie));
        when(movieMapper.toDto(movie)).thenReturn(movieDto);

        // Act
        MovieDto result = movieService.getMovieById(tmdbId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.tmdbId()).isEqualTo(tmdbId);
        assertThat(result.title()).isEqualTo("Fight Club");
        verify(movieRepository).findByTmdbId(tmdbId);
        verify(movieMapper).toDto(movie);
        verifyNoInteractions(tmdbClient);
    }

    /**
     * The read-through miss path: an absent movie must be fetched from TMDB and
     * persisted, so the next read is local. The two findByTmdbId invocations are
     * the double-check around the single-flight lock.
     */
    @Test
    @DisplayName("getMovieById - Should fetch from TMDB API when not in MongoDB")
    void getMovieById_WhenNotInMongoDB_ShouldFetchFromTMDB() {
        // Arrange
        Long tmdbId = 550L;
        TmdbMovieResponse tmdbResponse = createTestTmdbMovieResponse(tmdbId);
        Movie savedMovie = createTestMovie(tmdbId);
        MovieDto movieDto = createTestMovieDto(tmdbId);

        when(movieRepository.findByTmdbId(tmdbId)).thenReturn(Optional.empty());
        when(tmdbClient.getMovieDetails(tmdbId, tmdbApiKey)).thenReturn(tmdbResponse);
        when(movieRepository.save(any(Movie.class))).thenReturn(savedMovie);
        when(movieMapper.toDto(savedMovie)).thenReturn(movieDto);

        // Act
        MovieDto result = movieService.getMovieById(tmdbId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.tmdbId()).isEqualTo(tmdbId);
        verify(movieRepository, times(2)).findByTmdbId(tmdbId);
        verify(tmdbClient).getMovieDetails(tmdbId, tmdbApiKey);
        verify(movieRepository).save(any(Movie.class));
        verify(movieMapper).toDto(savedMovie);
    }

    /**
     * Discover must translate TMDB's list response into a PageResponse whose
     * totals (100 elements / 5 pages) come straight from TMDB — the pagination
     * metadata the UI relies on to render page controls.
     */
    @Test
    @DisplayName("discoverMovies - Should return paginated movies")
    void discoverMovies_ShouldReturnPaginatedMovies() {
        // Arrange
        TmdbMovieListResponse tmdbResponse = createTestTmdbMovieListResponse();
        when(tmdbClient.discoverMovies(tmdbApiKey, 1, "popularity.desc", null, null, null, null))
                .thenReturn(tmdbResponse);

        // Act
        PageResponse<MovieListDto> result = movieService.discoverMovies(1, 20, null, null, null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(100);
        assertThat(result.getTotalPages()).isEqualTo(5);
        verify(tmdbClient).discoverMovies(tmdbApiKey, 1, "popularity.desc", null, null, null, null);
    }

    /**
     * Discover filters (genre, year, min rating) must be passed through to the
     * TMDB client unchanged — verified by matching the exact client arguments,
     * since a dropped filter would silently widen the user's results.
     */
    @Test
    @DisplayName("discoverMovies - Should apply filters correctly")
    void discoverMovies_WithFilters_ShouldApplyFilters() {
        // Arrange
        Long genreId = 28L;
        Integer year = 2024;
        Double minRating = 7.5;
        TmdbMovieListResponse tmdbResponse = createTestTmdbMovieListResponse();
        
        when(tmdbClient.discoverMovies(tmdbApiKey, 1, "popularity.desc", genreId, year, minRating, null))
                .thenReturn(tmdbResponse);

        // Act
        PageResponse<MovieListDto> result = movieService.discoverMovies(1, 20, genreId, year, minRating);

        // Assert
        assertThat(result).isNotNull();
        verify(tmdbClient).discoverMovies(tmdbApiKey, 1, "popularity.desc", genreId, year, minRating, null);
    }

    /**
     * Search must forward the query to TMDB and return the mapped page — the
     * core search delegation that backs the app's search bar.
     */
    @Test
    @DisplayName("searchMovies - Should return search results")
    void searchMovies_ShouldReturnSearchResults() {
        // Arrange
        String query = "Inception";
        TmdbMovieListResponse tmdbResponse = createTestTmdbMovieListResponse();
        when(tmdbClient.searchMovies(tmdbApiKey, query, 1)).thenReturn(tmdbResponse);

        // Act
        PageResponse<MovieListDto> result = movieService.searchMovies(query, 1, 20);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        verify(tmdbClient).searchMovies(tmdbApiKey, query, 1);
    }

    /**
     * Trending must forward the time-window ("week"/"day") to TMDB — verified
     * on the client argument so the wrong window can't be silently substituted.
     */
    @Test
    @DisplayName("getTrendingMovies - Should return trending movies")
    void getTrendingMovies_ShouldReturnTrendingMovies() {
        // Arrange
        String timeWindow = "week";
        TmdbMovieListResponse tmdbResponse = createTestTmdbMovieListResponse();
        when(tmdbClient.getTrendingMovies(timeWindow, tmdbApiKey, 1)).thenReturn(tmdbResponse);

        // Act
        PageResponse<MovieListDto> result = movieService.getTrendingMovies(timeWindow, 1, 20);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        verify(tmdbClient).getTrendingMovies(timeWindow, tmdbApiKey, 1);
    }

    /**
     * Popular must delegate to the corresponding TMDB endpoint and return the
     * mapped page — one of the default home-screen category feeds.
     */
    @Test
    @DisplayName("getPopularMovies - Should return popular movies")
    void getPopularMovies_ShouldReturnPopularMovies() {
        // Arrange
        TmdbMovieListResponse tmdbResponse = createTestTmdbMovieListResponse();
        when(tmdbClient.getPopularMovies(tmdbApiKey, 1)).thenReturn(tmdbResponse);

        // Act
        PageResponse<MovieListDto> result = movieService.getPopularMovies(1, 20);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        verify(tmdbClient).getPopularMovies(tmdbApiKey, 1);
    }

    /**
     * Top-rated must delegate to its own TMDB endpoint — a distinct category
     * from popular, so it gets its own delegation check.
     */
    @Test
    @DisplayName("getTopRatedMovies - Should return top-rated movies")
    void getTopRatedMovies_ShouldReturnTopRatedMovies() {
        // Arrange
        TmdbMovieListResponse tmdbResponse = createTestTmdbMovieListResponse();
        when(tmdbClient.getTopRatedMovies(tmdbApiKey, 1)).thenReturn(tmdbResponse);

        // Act
        PageResponse<MovieListDto> result = movieService.getTopRatedMovies(1, 20);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        verify(tmdbClient).getTopRatedMovies(tmdbApiKey, 1);
    }

    /**
     * Videos must be fetched and converted to VideoDtos, with the trailer's
     * type preserved — the details page keys off {@code type == "Trailer"} to
     * pick which video to embed.
     */
    @Test
    @DisplayName("getMovieVideos - Should return movie videos")
    void getMovieVideos_ShouldReturnMovieVideos() {
        // Arrange
        Long movieId = 550L;
        TmdbVideosResponse tmdbResponse = createTestTmdbVideosResponse();
        when(tmdbClient.getMovieVideos(movieId, tmdbApiKey)).thenReturn(tmdbResponse);
        when(movieMapper.toDto(any(Video.class))).thenAnswer(inv -> {
            Video v = inv.getArgument(0);
            return VideoDto.builder()
                    .id(v.getId()).key(v.getKey()).name(v.getName()).site(v.getSite())
                    .size(v.getSize()).type(v.getType()).official(v.getOfficial())
                    .publishedAt(v.getPublishedAt()).build();
        });

        // Act
        List<VideoDto> result = movieService.getMovieVideos(movieId);

        // Assert
        assertThat(result)
                .isNotNull()
                .hasSize(2);
        assertThat(result.get(0).type()).isEqualTo("Trailer");
        verify(tmdbClient).getMovieVideos(movieId, tmdbApiKey);
    }

    /**
     * Credits must split TMDB's response into cast and crew and preserve
     * top-billed order (Brad Pitt first) — the details page renders the cast
     * list in exactly this order.
     */
    @Test
    @DisplayName("getMovieCredits - Should return movie credits")
    void getMovieCredits_ShouldReturnMovieCredits() {
        // Arrange
        Long movieId = 550L;
        TmdbCreditsResponse tmdbResponse = createTestTmdbCreditsResponse();
        when(tmdbClient.getMovieCredits(movieId, tmdbApiKey)).thenReturn(tmdbResponse);
        when(movieMapper.toDto(any(Credits.class))).thenAnswer(inv -> {
            Credits c = inv.getArgument(0);
            return CreditsDto.builder()
                    .movieId(c.getMovieId())
                    .cast(c.getCast().stream()
                            .map(cast -> CastDto.builder()
                                    .id(cast.getId()).name(cast.getName()).character(cast.getCharacter())
                                    .profilePath(cast.getProfilePath()).order(cast.getOrder()).build())
                            .toList())
                    .crew(c.getCrew().stream()
                            .map(crew -> CrewDto.builder()
                                    .id(crew.getId()).name(crew.getName()).job(crew.getJob())
                                    .department(crew.getDepartment()).profilePath(crew.getProfilePath()).build())
                            .toList())
                    .build();
        });

        // Act
        CreditsDto result = movieService.getMovieCredits(movieId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.movieId()).isEqualTo(movieId);
        assertThat(result.cast()).hasSize(2);
        assertThat(result.crew()).hasSize(1);
        assertThat(result.cast().get(0).name()).isEqualTo("Brad Pitt");
        verify(tmdbClient).getMovieCredits(movieId, tmdbApiKey);
    }

    /**
     * Similar must delegate per-movie to TMDB and return the mapped page — a
     * details-page rail, verified on the client call with the movie id.
     */
    @Test
    @DisplayName("getSimilarMovies - Should return similar movies")
    void getSimilarMovies_ShouldReturnSimilarMovies() {
        // Arrange
        Long movieId = 550L;
        TmdbMovieListResponse tmdbResponse = createTestTmdbMovieListResponse();
        when(tmdbClient.getSimilarMovies(movieId, tmdbApiKey, 1)).thenReturn(tmdbResponse);

        // Act
        PageResponse<MovieListDto> result = movieService.getSimilarMovies(movieId, 1, 20);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        verify(tmdbClient).getSimilarMovies(movieId, tmdbApiKey, 1);
    }

    /**
     * Recommendations must delegate per-movie to TMDB — the sibling of similar
     * movies (different TMDB endpoint), so it needs its own delegation check.
     */
    @Test
    @DisplayName("getRecommendedMovies - Should return recommended movies")
    void getRecommendedMovies_ShouldReturnRecommendedMovies() {
        // Arrange
        Long movieId = 550L;
        TmdbMovieListResponse tmdbResponse = createTestTmdbMovieListResponse();
        when(tmdbClient.getRecommendedMovies(movieId, tmdbApiKey, 1)).thenReturn(tmdbResponse);

        // Act
        PageResponse<MovieListDto> result = movieService.getRecommendedMovies(movieId, 1, 20);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        verify(tmdbClient).getRecommendedMovies(movieId, tmdbApiKey, 1);
    }

    /**
     * The genre list must be fetched and each Genre mapped to a GenreDto in
     * order — this feeds the app's genre sidebar, so both content and ordering
     * matter.
     */
    @Test
    @DisplayName("getAllGenres - Should return all genres")
    void getAllGenres_ShouldReturnAllGenres() {
        // Arrange
        TmdbGenresResponse tmdbResponse = createTestTmdbGenresResponse();
        Genre genre1 = Genre.builder().id(28L).name("Action").build();
        Genre genre2 = Genre.builder().id(18L).name("Drama").build();
        GenreDto genreDto1 = GenreDto.builder().id(28L).name("Action").build();
        GenreDto genreDto2 = GenreDto.builder().id(18L).name("Drama").build();

        when(tmdbClient.getGenres(tmdbApiKey)).thenReturn(tmdbResponse);
        when(movieMapper.toDto(genre1)).thenReturn(genreDto1);
        when(movieMapper.toDto(genre2)).thenReturn(genreDto2);

        // Act
        List<GenreDto> result = movieService.getAllGenres();

        // Assert
        assertThat(result)
                .isNotNull()
                .hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Action");
        assertThat(result.get(1).name()).isEqualTo("Drama");
        verify(tmdbClient).getGenres(tmdbApiKey);
    }

    // Schema-drift self-healing (issue #46)

    /**
     * The core of #46: a document persisted by an older model version cannot be
     * converted, and before the fix that exception escaped as a 500 on every
     * request forever, because nothing ever replaced the bad document.
     *
     * <p>Under ADR-010 the catalog is re-derivable from TMDB, so an unreadable
     * document is semantically a cache miss. The service must therefore swallow
     * the conversion error, drop the document, and fetch fresh data — the
     * request succeeds and the stored shape is repaired as a side effect.</p>
     */
    @Test
    @DisplayName("getMovieById - Should treat an unconvertible document as a miss and re-fetch")
    void getMovieById_WhenPersistedDocumentCannotBeConverted_ShouldEvictAndRefetch() {
        // Given: MongoDB holds a document the current model cannot read — the
        // real-world shape of this is spokenLanguages stored as List<String>
        // before ADR-010 changed it to List<SpokenLanguage>.
        // The first read throws; the second (the double-check inside the
        // single-flight lock) sees the document already gone, mirroring the
        // eviction's real side effect.
        Long tmdbId = 550L;
        when(movieRepository.findByTmdbId(tmdbId))
                .thenThrow(new ConverterNotFoundException(
                        TypeDescriptor.valueOf(String.class),
                        TypeDescriptor.valueOf(SpokenLanguage.class)))
                .thenReturn(Optional.empty());

        TmdbMovieResponse tmdbResponse = createTestTmdbMovieResponse(tmdbId);
        Movie savedMovie = createTestMovie(tmdbId);
        MovieDto movieDto = createTestMovieDto(tmdbId);
        when(tmdbClient.getMovieDetails(tmdbId, tmdbApiKey)).thenReturn(tmdbResponse);
        when(movieRepository.save(any(Movie.class))).thenReturn(savedMovie);
        when(movieMapper.toDto(savedMovie)).thenReturn(movieDto);
        when(mongoTemplate.remove(any(Query.class), eq(Movie.class)))
                .thenReturn(DeleteResult.acknowledged(1));

        // When
        MovieDto result = movieService.getMovieById(tmdbId);

        // Then: the caller sees a normal successful response, not an error...
        assertThat(result).isNotNull();
        assertThat(result.tmdbId()).isEqualTo(tmdbId);

        // ...the poisoned document was removed by query (never converted)...
        verify(mongoTemplate).remove(any(Query.class), eq(Movie.class));

        // ...and the movie was re-fetched and re-persisted in the current shape.
        verify(tmdbClient).getMovieDetails(tmdbId, tmdbApiKey);
        verify(movieRepository).save(any(Movie.class));
    }

    /**
     * The safety boundary on the above. Only mapping/conversion problems mean
     * "this document is stale"; a {@code DataAccessException} means MongoDB
     * itself is unavailable. Swallowing that would convert an outage into a
     * silent flood of TMDB calls (and blow the rate limit), so it must
     * propagate and it must NOT delete anything.
     */
    @Test
    @DisplayName("getMovieById - Should propagate infrastructure failures instead of masking them as a miss")
    void getMovieById_WhenMongoIsUnavailable_ShouldPropagateAndNotEvict() {
        // Given: MongoDB is down (not schema drift)
        Long tmdbId = 550L;
        when(movieRepository.findByTmdbId(tmdbId))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        // When / Then: the failure surfaces rather than being treated as a miss
        assertThatThrownBy(() -> movieService.getMovieById(tmdbId))
                .isInstanceOf(DataAccessResourceFailureException.class);

        // And nothing was deleted, and TMDB was never hit
        verifyNoInteractions(tmdbClient);
        verify(mongoTemplate, never()).remove(any(Query.class), eq(Movie.class));
    }

    /**
     * Eviction is best-effort: if the cleanup delete itself fails, the user's
     * request must still succeed on freshly fetched data. Losing the repair is
     * acceptable (it retries next time); failing the request is not.
     */
    @Test
    @DisplayName("getMovieById - Should still serve fresh data when evicting the bad document fails")
    void getMovieById_WhenEvictionFails_ShouldStillReturnFreshlyFetchedMovie() {
        // Given: an unreadable document AND a delete that fails
        Long tmdbId = 550L;
        when(movieRepository.findByTmdbId(tmdbId)).thenThrow(
                new ConverterNotFoundException(
                        TypeDescriptor.valueOf(String.class),
                        TypeDescriptor.valueOf(SpokenLanguage.class)));
        when(mongoTemplate.remove(any(Query.class), eq(Movie.class)))
                .thenThrow(new DataAccessResourceFailureException("delete failed"));

        TmdbMovieResponse tmdbResponse = createTestTmdbMovieResponse(tmdbId);
        Movie savedMovie = createTestMovie(tmdbId);
        MovieDto movieDto = createTestMovieDto(tmdbId);
        when(tmdbClient.getMovieDetails(tmdbId, tmdbApiKey)).thenReturn(tmdbResponse);
        when(movieRepository.save(any(Movie.class))).thenReturn(savedMovie);
        when(movieMapper.toDto(savedMovie)).thenReturn(movieDto);

        // When
        MovieDto result = movieService.getMovieById(tmdbId);

        // Then: the request succeeded anyway
        assertThat(result).isNotNull();
        assertThat(result.tmdbId()).isEqualTo(tmdbId);
        verify(tmdbClient).getMovieDetails(tmdbId, tmdbApiKey);
    }

    // Helper methods

    /**
     * Builds a fully-populated Movie entity (MongoDB-hit stubbing).
     *
     * @param tmdbId TMDB id to embed
     * @return a test movie
     */
    private Movie createTestMovie(Long tmdbId) {
        return Movie.builder()
                .id("mongo123")
                .tmdbId(tmdbId)
                .title("Fight Club")
                .overview("An insomniac office worker...")
                .posterPath("/poster.jpg")
                .backdropPath("/backdrop.jpg")
                .releaseDate(LocalDate.of(1999, 10, 15))
                .voteAverage(8.4)
                .voteCount(25000)
                .genres(Arrays.asList(
                        Genre.builder().id(18L).name("Drama").build(),
                        Genre.builder().id(53L).name("Thriller").build()
                ))
                .runtime(139)
                .status("Released")
                .budget(63000000L)
                .revenue(100853753L)
                .popularity(450.5)
                .adult(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Builds the DTO the mapper is stubbed to return.
     *
     * @param tmdbId TMDB id to embed
     * @return a test DTO
     */
    private MovieDto createTestMovieDto(Long tmdbId) {
        return MovieDto.builder()
                .id("mongo123")
                .tmdbId(tmdbId)
                .title("Fight Club")
                .overview("An insomniac office worker...")
                .voteAverage(8.4)
                .runtime(139)
                .build();
    }

    /**
     * Builds the raw TMDB detail response returned on a cache miss.
     *
     * @param tmdbId TMDB id to embed
     * @return a test TMDB movie response
     */
    private TmdbMovieResponse createTestTmdbMovieResponse(Long tmdbId) {
        return new TmdbMovieResponse(
            tmdbId,
            "Fight Club",
            "Fight Club",
            "An insomniac office worker...",
            "/poster.jpg",
            "/backdrop.jpg",
            LocalDate.of(1999, 10, 15),
            8.4,
            25000,
            Arrays.asList(
                Genre.builder().id(18L).name("Drama").build(),
                Genre.builder().id(53L).name("Thriller").build()
            ),
            139,
            "Released",
            63000000L,
            100853753L,
            null,
            null,
            null,
            null,
            false,
            "en",
            450.5,
            false,
            null,
            null,
            null,
            null,
            null
        );
    }

    /**
     * Builds a two-item TMDB list response with totals (page 1 of 5, 100
     * results) used by all the list-endpoint tests.
     *
     * @return a test TMDB list response
     */
    private TmdbMovieListResponse createTestTmdbMovieListResponse() {
        TmdbMovieListResponse.TmdbMovieItem item1 = new TmdbMovieListResponse.TmdbMovieItem(
            550L,
            "Fight Club",
            "An insomniac office worker...",
            "/poster.jpg",
            "/backdrop.jpg",
            "1999-10-15",
            8.4,
            25000,
            null,
            450.5,
            false,
            "en"
        );

        TmdbMovieListResponse.TmdbMovieItem item2 = new TmdbMovieListResponse.TmdbMovieItem(
            13L,
            "Forrest Gump",
            "A man with a low IQ...",
            "/poster2.jpg",
            null,
            "1994-07-06",
            8.8,
            22000,
            null,
            320.3,
            false,
            "en"
        );

        return new TmdbMovieListResponse(1, 5, 100, Arrays.asList(item1, item2));
    }

    /**
     * Builds a videos response with a Trailer and a Featurette (order matters:
     * the trailer is first, which the videos test asserts).
     *
     * @return a test TMDB videos response
     */
    private TmdbVideosResponse createTestTmdbVideosResponse() {
        TmdbVideosResponse.TmdbVideo video1 = new TmdbVideosResponse.TmdbVideo(
            "video123",
            "dQw4w9WgXcQ",
            "Official Trailer",
            "YouTube",
            1080,
            "Trailer",
            true,
            "2020-01-01T00:00:00Z"
        );

        TmdbVideosResponse.TmdbVideo video2 = new TmdbVideosResponse.TmdbVideo(
            "video456",
            "abc123xyz",
            "Behind the Scenes",
            "YouTube",
            720,
            "Featurette",
            true,
            "2020-02-01T00:00:00Z"
        );

        return new TmdbVideosResponse(550L, Arrays.asList(video1, video2));
    }

    /**
     * Builds a credits response with two cast (billing order 0, 1) and one
     * crew member (director), matching the credits test's expectations.
     *
     * @return a test TMDB credits response
     */
    private TmdbCreditsResponse createTestTmdbCreditsResponse() {
        TmdbCreditsResponse.TmdbCast cast1 = new TmdbCreditsResponse.TmdbCast(
            287L,
            "Brad Pitt",
            "Tyler Durden",
            "/brad.jpg",
            0,
            null
        );

        TmdbCreditsResponse.TmdbCast cast2 = new TmdbCreditsResponse.TmdbCast(
            819L,
            "Edward Norton",
            "The Narrator",
            "/norton.jpg",
            1,
            null
        );

        TmdbCreditsResponse.TmdbCrew crew1 = new TmdbCreditsResponse.TmdbCrew(
            7467L,
            "David Fincher",
            "Director",
            "Directing",
            "/fincher.jpg",
            null
        );

        return new TmdbCreditsResponse(550L, Arrays.asList(cast1, cast2), Arrays.asList(crew1));
    }

    /**
     * Builds a two-genre TMDB response (Action, Drama) for the genres test.
     *
     * @return a test TMDB genres response
     */
    private TmdbGenresResponse createTestTmdbGenresResponse() {
        return new TmdbGenresResponse(
            Arrays.asList(
                Genre.builder().id(28L).name("Action").build(),
                Genre.builder().id(18L).name("Drama").build()
            )
        );
    }
}

