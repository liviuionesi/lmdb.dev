package com.filmpire.movie.service;

import com.filmpire.movie.client.TmdbClient;
import com.filmpire.movie.client.dto.*;
import com.filmpire.movie.dto.*;
import com.filmpire.movie.mapper.MovieMapper;
import com.filmpire.movie.model.Genre;
import com.filmpire.movie.model.Movie;
import com.filmpire.movie.repository.MovieRepository;
import com.filmpire.shared.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MovieService.
 * Tests business logic with mocked dependencies.
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

    @InjectMocks
    private MovieService movieService;

    private final String tmdbApiKey = "test-api-key";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(movieService, "tmdbApiKey", tmdbApiKey);
    }

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

    @Test
    @DisplayName("discoverMovies - Should return paginated movies")
    void discoverMovies_ShouldReturnPaginatedMovies() {
        // Arrange
        TmdbMovieListResponse tmdbResponse = createTestTmdbMovieListResponse();
        when(tmdbClient.discoverMovies(tmdbApiKey, 1, "popularity.desc", null, null, null))
                .thenReturn(tmdbResponse);

        // Act
        PageResponse<MovieListDto> result = movieService.discoverMovies(1, 20, null, null, null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(100);
        assertThat(result.getTotalPages()).isEqualTo(5);
        verify(tmdbClient).discoverMovies(tmdbApiKey, 1, "popularity.desc", null, null, null);
    }

    @Test
    @DisplayName("discoverMovies - Should apply filters correctly")
    void discoverMovies_WithFilters_ShouldApplyFilters() {
        // Arrange
        Long genreId = 28L;
        Integer year = 2024;
        Double minRating = 7.5;
        TmdbMovieListResponse tmdbResponse = createTestTmdbMovieListResponse();
        
        when(tmdbClient.discoverMovies(tmdbApiKey, 1, "popularity.desc", genreId, year, minRating))
                .thenReturn(tmdbResponse);

        // Act
        PageResponse<MovieListDto> result = movieService.discoverMovies(1, 20, genreId, year, minRating);

        // Assert
        assertThat(result).isNotNull();
        verify(tmdbClient).discoverMovies(tmdbApiKey, 1, "popularity.desc", genreId, year, minRating);
    }

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

    @Test
    @DisplayName("getMovieVideos - Should return movie videos")
    void getMovieVideos_ShouldReturnMovieVideos() {
        // Arrange
        Long movieId = 550L;
        TmdbVideosResponse tmdbResponse = createTestTmdbVideosResponse();
        when(tmdbClient.getMovieVideos(movieId, tmdbApiKey)).thenReturn(tmdbResponse);

        // Act
        List<VideoDto> result = movieService.getMovieVideos(movieId);

        // Assert
        assertThat(result)
                .isNotNull()
                .hasSize(2);
        assertThat(result.get(0).type()).isEqualTo("Trailer");
        verify(tmdbClient).getMovieVideos(movieId, tmdbApiKey);
    }

    @Test
    @DisplayName("getMovieCredits - Should return movie credits")
    void getMovieCredits_ShouldReturnMovieCredits() {
        // Arrange
        Long movieId = 550L;
        TmdbCreditsResponse tmdbResponse = createTestTmdbCreditsResponse();
        when(tmdbClient.getMovieCredits(movieId, tmdbApiKey)).thenReturn(tmdbResponse);

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

    // Helper methods

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

    private TmdbMovieResponse createTestTmdbMovieResponse(Long tmdbId) {
        return new TmdbMovieResponse(
            tmdbId,
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
            "en",
            450.5,
            false,
            null,
            null,
            null
        );
    }

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

    private TmdbGenresResponse createTestTmdbGenresResponse() {
        return new TmdbGenresResponse(
            Arrays.asList(
                Genre.builder().id(28L).name("Action").build(),
                Genre.builder().id(18L).name("Drama").build()
            )
        );
    }
}

