package com.filmpire.movie.config;

import com.filmpire.movie.client.TmdbClient;
import com.filmpire.movie.client.dto.*;
import com.filmpire.movie.model.Genre;
import com.filmpire.movie.model.ProductionCompany;
import com.filmpire.movie.model.SpokenLanguage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.util.List;

/**
 * Test-only configuration that overrides the real TMDB Http client
 * with an in-memory stub. This prevents integration tests from
 * calling the real TMDB API while still exercising the full
 * controller -> service -> caching -> MongoDB stack.
 * <p>
 * Every stub answer is deterministic and derived from the input arguments (movie id, page), so
 * integration tests can predict exact field values without network access or WireMock. Import
 * it into a test with {@code @Import(TmdbClientTestStubConfig.class)}; {@code @Primary} makes
 * it win over the auto-configured HTTP-interface proxy.
 */
@TestConfiguration
public class TmdbClientTestStubConfig {

    /**
     * Supplies the stubbed {@link TmdbClient} as the primary bean so it transparently replaces
     * the real declarative HTTP client everywhere it is injected. Implemented as an anonymous
     * class rather than Mockito so all methods have real, consistent behavior by default.
     */
    @Bean
    @Primary
    public TmdbClient tmdbClientStub() {
        return new TmdbClient() {
            /**
             * Returns a fully populated details record whose title, paths, imdb id and homepage
             * embed the requested movie id, letting callers assert that the id they asked for
             * is the one that flowed through the service and persistence layers.
             */
            @Override
            public TmdbMovieResponse getMovieDetails(Long movieId, String apiKey) {
                return new TmdbMovieResponse(
                    movieId,
                    "Stub Movie " + movieId,
                    "Original Stub Movie " + movieId,
                    "Overview for stub movie " + movieId,
                    "/stub/poster-" + movieId + ".jpg",
                    "/stub/backdrop-" + movieId + ".jpg",
                    LocalDate.of(2000, 1, 1),
                    8.0,
                    1000,
                    List.of(Genre.builder().id(28L).name("Action").build()),
                    120,
                    "Released",
                    10000000L,
                    50000000L,
                    List.of(new SpokenLanguage("en", "English")),
                    List.of(ProductionCompany.builder()
                        .id(1L)
                        .name("Stub Studio")
                        .logoPath(null)
                        .originCountry("US")
                        .build()),
                    List.of(),
                    null,
                    false,
                    "en",
                    100.0,
                    false,
                    "tt-stub-" + movieId,
                    "Stub tagline",
                    "https://example.com/stub/" + movieId,
                    null,
                    null
                );
            }

            /**
             * Ignores every filter (sort, genre, year, rating, cast) and returns the shared
             * page-derived list; filter correctness is not this stub's concern.
             */
            @Override
            public TmdbMovieListResponse discoverMovies(String apiKey, Integer page, String sortBy,
                                                         Long genreId, Integer year, Double minRating,
                                                         Long castId) {
                return buildStubMovieList(page);
            }

            /** Ignores the query text; any search yields the shared page-derived list. */
            @Override
            public TmdbMovieListResponse searchMovies(String apiKey, String query, Integer page) {
                return buildStubMovieList(page);
            }

            /** Ignores the time window ("day"/"week"); trending is the shared list. */
            @Override
            public TmdbMovieListResponse getTrendingMovies(String timeWindow, String apiKey, Integer page) {
                return buildStubMovieList(page);
            }

            /** Popular movies are the shared page-derived list. */
            @Override
            public TmdbMovieListResponse getPopularMovies(String apiKey, Integer page) {
                return buildStubMovieList(page);
            }

            /** Top-rated movies are the shared page-derived list. */
            @Override
            public TmdbMovieListResponse getTopRatedMovies(String apiKey, Integer page) {
                return buildStubMovieList(page);
            }

            /** Upcoming movies are the shared page-derived list. */
            @Override
            public TmdbMovieListResponse getUpcomingMovies(String apiKey, Integer page) {
                return buildStubMovieList(page);
            }

            /** Now-playing movies are the shared page-derived list. */
            @Override
            public TmdbMovieListResponse getNowPlayingMovies(String apiKey, Integer page) {
                return buildStubMovieList(page);
            }

            /**
             * Returns a single official YouTube trailer whose id and key embed the movie id,
             * so video lookups can be traced back to the requested movie in assertions.
             */
            @Override
            public TmdbVideosResponse getMovieVideos(Long movieId, String apiKey) {
                TmdbVideosResponse.TmdbVideo video = new TmdbVideosResponse.TmdbVideo(
                    "vid-" + movieId,
                    "stub-key-" + movieId,
                    "Stub Trailer",
                    "YouTube",
                    1080,
                    "Trailer",
                    true,
                    "2020-01-01T00:00:00Z"
                );
                return new TmdbVideosResponse(movieId, List.of(video));
            }

            /**
             * Returns one cast member and one crew member (a director), the minimum shape
             * needed to verify that both credit collections survive the mapping pipeline.
             */
            @Override
            public TmdbCreditsResponse getMovieCredits(Long movieId, String apiKey) {
                TmdbCreditsResponse.TmdbCast cast = new TmdbCreditsResponse.TmdbCast(
                    1L,
                    "Stub Actor",
                    "Stub Character",
                    "/stub/actor.jpg",
                    0,
                    100L
                );

                TmdbCreditsResponse.TmdbCrew crew = new TmdbCreditsResponse.TmdbCrew(
                    2L,
                    "Stub Director",
                    "Director",
                    "Directing",
                    "/stub/director.jpg",
                    "crew-1"
                );

                return new TmdbCreditsResponse(movieId, List.of(cast), List.of(crew));
            }

            @Override
            public TmdbMovieListResponse getSimilarMovies(Long movieId, String apiKey, Integer page) {
                return buildStubMovieList(page);
            }

            @Override
            public TmdbMovieListResponse getRecommendedMovies(Long movieId, String apiKey, Integer page) {
                return buildStubMovieList(page);
            }

            @Override
            public TmdbGenresResponse getGenres(String apiKey) {
                return new TmdbGenresResponse(
                    List.of(
                        Genre.builder().id(28L).name("Action").build(),
                        Genre.builder().id(18L).name("Drama").build()
                    )
                );
            }

            private TmdbMovieListResponse buildStubMovieList(Integer page) {
                TmdbMovieListResponse.TmdbMovieItem item1 = new TmdbMovieListResponse.TmdbMovieItem(
                    100L + page,
                    "Stub List Movie " + page,
                    "Overview for stub list movie " + page,
                    "/stub/list-poster-" + page + ".jpg",
                    "/stub/list-backdrop-" + page + ".jpg",
                    "2020-01-01",
                    7.5,
                    500,
                    List.of(28L),
                    80.0,
                    false,
                    "en"
                );

                TmdbMovieListResponse.TmdbMovieItem item2 = new TmdbMovieListResponse.TmdbMovieItem(
                    200L + page,
                    "Stub List Movie 2 " + page,
                    "Overview for stub list movie 2 " + page,
                    "/stub/list-poster-2-" + page + ".jpg",
                    "/stub/list-backdrop-2-" + page + ".jpg",
                    "2020-02-02",
                    8.5,
                    600,
                    List.of(18L),
                    90.0,
                    false,
                    "en"
                );

                return new TmdbMovieListResponse(page, 10, 100, List.of(item1, item2));
            }
        };
    }
}
