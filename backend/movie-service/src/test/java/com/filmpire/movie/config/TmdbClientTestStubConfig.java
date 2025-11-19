package com.filmpire.movie.config;

import com.filmpire.movie.client.TmdbClient;
import com.filmpire.movie.client.dto.*;
import com.filmpire.movie.model.Genre;
import com.filmpire.movie.model.ProductionCompany;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.util.List;

/**
 * Test-only configuration that overrides the real TMDB Feign client
 * with an in-memory stub. This prevents integration tests from
 * calling the real TMDB API while still exercising the full
 * controller -> service -> caching -> MongoDB stack.
 */
@TestConfiguration
public class TmdbClientTestStubConfig {

    @Bean
    @Primary
    public TmdbClient tmdbClientStub() {
        return new TmdbClient() {
            @Override
            public TmdbMovieResponse getMovieDetails(Long movieId, String apiKey) {
                TmdbMovieResponse response = new TmdbMovieResponse();
                response.setId(movieId);
                response.setTitle("Stub Movie " + movieId);
                response.setOverview("Overview for stub movie " + movieId);
                response.setPosterPath("/stub/poster-" + movieId + ".jpg");
                response.setBackdropPath("/stub/backdrop-" + movieId + ".jpg");
                response.setReleaseDate(LocalDate.of(2000, 1, 1));
                response.setVoteAverage(8.0);
                response.setVoteCount(1000);
                response.setGenres(List.of(new Genre(28L, "Action")));
                response.setRuntime(120);
                response.setStatus("Released");
                response.setBudget(10000000L);
                response.setRevenue(50000000L);
                response.setSpokenLanguages(List.of());
                response.setProductionCompanies(List.of(
                        new ProductionCompany(1L, "Stub Studio", null, "US")
                ));
                response.setOriginalLanguage("en");
                response.setPopularity(100.0);
                response.setAdult(false);
                response.setImdbId("tt-stub-" + movieId);
                response.setTagline("Stub tagline");
                response.setHomepage("https://example.com/stub/" + movieId);
                return response;
            }

            @Override
            public TmdbMovieListResponse discoverMovies(String apiKey, Integer page, String sortBy,
                                                         Long genreId, Integer year, Double minRating) {
                return buildStubMovieList(page);
            }

            @Override
            public TmdbMovieListResponse searchMovies(String apiKey, String query, Integer page) {
                return buildStubMovieList(page);
            }

            @Override
            public TmdbMovieListResponse getTrendingMovies(String timeWindow, String apiKey, Integer page) {
                return buildStubMovieList(page);
            }

            @Override
            public TmdbMovieListResponse getPopularMovies(String apiKey, Integer page) {
                return buildStubMovieList(page);
            }

            @Override
            public TmdbMovieListResponse getTopRatedMovies(String apiKey, Integer page) {
                return buildStubMovieList(page);
            }

            @Override
            public TmdbVideosResponse getMovieVideos(Long movieId, String apiKey) {
                TmdbVideosResponse response = new TmdbVideosResponse();
                response.setId(movieId);
                TmdbVideosResponse.TmdbVideo video = new TmdbVideosResponse.TmdbVideo();
                video.setId("vid-" + movieId);
                video.setKey("stub-key-" + movieId);
                video.setName("Stub Trailer");
                video.setSite("YouTube");
                video.setSize(1080);
                video.setType("Trailer");
                video.setOfficial(true);
                video.setPublishedAt("2020-01-01T00:00:00Z");
                response.setResults(List.of(video));
                return response;
            }

            @Override
            public TmdbCreditsResponse getMovieCredits(Long movieId, String apiKey) {
                TmdbCreditsResponse response = new TmdbCreditsResponse();
                response.setId(movieId);

                TmdbCreditsResponse.TmdbCast cast = new TmdbCreditsResponse.TmdbCast();
                cast.setId(1L);
                cast.setName("Stub Actor");
                cast.setCharacter("Stub Character");
                cast.setProfilePath("/stub/actor.jpg");
                cast.setOrder(0);
                cast.setCastId(100L);

                TmdbCreditsResponse.TmdbCrew crew = new TmdbCreditsResponse.TmdbCrew();
                crew.setId(2L);
                crew.setName("Stub Director");
                crew.setJob("Director");
                crew.setDepartment("Directing");
                crew.setProfilePath("/stub/director.jpg");
                crew.setCreditId("crew-1");

                response.setCast(List.of(cast));
                response.setCrew(List.of(crew));
                return response;
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
                TmdbGenresResponse response = new TmdbGenresResponse();
                response.setGenres(List.of(
                        new Genre(28L, "Action"),
                        new Genre(18L, "Drama")
                ));
                return response;
            }

            private TmdbMovieListResponse buildStubMovieList(Integer page) {
                TmdbMovieListResponse list = new TmdbMovieListResponse();
                list.setPage(page);
                list.setTotalPages(10);
                list.setTotalResults(100);

                TmdbMovieListResponse.TmdbMovieItem item = new TmdbMovieListResponse.TmdbMovieItem();
                item.setId(100L + page);
                item.setTitle("Stub List Movie " + page);
                item.setOverview("Overview for stub list movie " + page);
                item.setPosterPath("/stub/list-poster-" + page + ".jpg");
                item.setBackdropPath("/stub/list-backdrop-" + page + ".jpg");
                item.setReleaseDate("2020-01-01");
                item.setVoteAverage(7.5);
                item.setVoteCount(500);
                item.setGenreIds(List.of(28L));
                item.setPopularity(80.0);
                item.setAdult(false);
                item.setOriginalLanguage("en");

                list.setResults(List.of(item));
                return list;
            }
        };
    }
}
