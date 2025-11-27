package com.filmpire.movie.controller;

import com.filmpire.movie.dto.*;
import com.filmpire.movie.service.MovieService;
import com.filmpire.shared.dto.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for MovieController.
 * Tests REST endpoints with mocked service layer.
 */
@WebMvcTest(MovieController.class)
@DisplayName("MovieController Tests")
class MovieControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private MovieService movieService;

    @Autowired
    MovieControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("GET /api/v1/movies/{id} - Should return movie details")
    void getMovieById_ShouldReturnMovieDetails() throws Exception {
        // Arrange
        Long movieId = 550L;
        MovieDto movieDto = createTestMovieDto(movieId);
        when(movieService.getMovieById(movieId)).thenReturn(movieDto);

        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/{id}", movieId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tmdbId").value(movieId))
                .andExpect(jsonPath("$.title").value("Fight Club"))
                .andExpect(jsonPath("$.voteAverage").value(8.4))
                .andExpect(jsonPath("$.runtime").value(139))
                .andExpect(jsonPath("$.genres", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/v1/movies/discover - Should return paginated movies")
    void discoverMovies_ShouldReturnPaginatedMovies() throws Exception {
        // Arrange
        PageResponse<MovieListDto> pageResponse = createTestPageResponse();
        when(movieService.discoverMovies(1, 20, null, null, null))
                .thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/discover")
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(100))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.pageNumber").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/movies/discover - Should apply genre filter")
    void discoverMovies_WithGenreFilter_ShouldReturnFilteredMovies() throws Exception {
        // Arrange
        Long genreId = 28L;
        PageResponse<MovieListDto> pageResponse = createTestPageResponse();
        when(movieService.discoverMovies(1, 20, genreId, null, null))
                .thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/discover")
                .param("page", "1")
                .param("size", "20")
                .param("genreId", genreId.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/v1/movies/discover - Should apply year and rating filters")
    void discoverMovies_WithYearAndRatingFilters_ShouldReturnFilteredMovies() throws Exception {
        // Arrange
        Integer year = 2024;
        Double minRating = 7.0;
        PageResponse<MovieListDto> pageResponse = createTestPageResponse();
        when(movieService.discoverMovies(1, 20, null, year, minRating))
                .thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/discover")
                .param("page", "1")
                .param("size", "20")
                .param("year", year.toString())
                .param("minRating", minRating.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/movies/search - Should return search results")
    void searchMovies_ShouldReturnSearchResults() throws Exception {
        // Arrange
        String query = "Inception";
        PageResponse<MovieListDto> pageResponse = createTestPageResponse();
        when(movieService.searchMovies(query, 1, 20)).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/search")
                .param("query", query)
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/v1/movies/trending - Should return trending movies")
    void getTrendingMovies_ShouldReturnTrendingMovies() throws Exception {
        // Arrange
        PageResponse<MovieListDto> pageResponse = createTestPageResponse();
        when(movieService.getTrendingMovies("week", 1, 20)).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/trending")
                .param("timeWindow", "week")
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/movies/trending - Should use default time window")
    void getTrendingMovies_WithDefaultTimeWindow_ShouldUseWeek() throws Exception {
        // Arrange
        PageResponse<MovieListDto> pageResponse = createTestPageResponse();
        when(movieService.getTrendingMovies("week", 1, 20)).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/trending")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/movies/popular - Should return popular movies")
    void getPopularMovies_ShouldReturnPopularMovies() throws Exception {
        // Arrange
        PageResponse<MovieListDto> pageResponse = createTestPageResponse();
        when(movieService.getPopularMovies(1, 20)).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/popular")
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/movies/top-rated - Should return top-rated movies")
    void getTopRatedMovies_ShouldReturnTopRatedMovies() throws Exception {
        // Arrange
        PageResponse<MovieListDto> pageResponse = createTestPageResponse();
        when(movieService.getTopRatedMovies(1, 20)).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/top-rated")
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/movies/{id}/videos - Should return movie videos")
    void getMovieVideos_ShouldReturnVideos() throws Exception {
        // Arrange
        Long movieId = 550L;
        List<VideoDto> videos = createTestVideos();
        when(movieService.getMovieVideos(movieId)).thenReturn(videos);

        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/{id}/videos", movieId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("Trailer"))
                .andExpect(jsonPath("$[0].site").value("YouTube"));
    }

    @Test
    @DisplayName("GET /api/v1/movies/{id}/credits - Should return movie credits")
    void getMovieCredits_ShouldReturnCredits() throws Exception {
        // Arrange
        Long movieId = 550L;
        CreditsDto credits = createTestCredits(movieId);
        when(movieService.getMovieCredits(movieId)).thenReturn(credits);

        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/{id}/credits", movieId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieId").value(movieId))
                .andExpect(jsonPath("$.cast", hasSize(2)))
                .andExpect(jsonPath("$.crew", hasSize(1)))
                .andExpect(jsonPath("$.cast[0].name").value("Brad Pitt"));
    }

    @Test
    @DisplayName("GET /api/v1/movies/{id}/similar - Should return similar movies")
    void getSimilarMovies_ShouldReturnSimilarMovies() throws Exception {
        // Arrange
        Long movieId = 550L;
        PageResponse<MovieListDto> pageResponse = createTestPageResponse();
        when(movieService.getSimilarMovies(movieId, 1, 20)).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/{id}/similar", movieId)
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/movies/{id}/recommendations - Should return recommended movies")
    void getRecommendedMovies_ShouldReturnRecommendations() throws Exception {
        // Arrange
        Long movieId = 550L;
        PageResponse<MovieListDto> pageResponse = createTestPageResponse();
        when(movieService.getRecommendedMovies(movieId, 1, 20)).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/movies/{id}/recommendations", movieId)
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // Helper methods

    private MovieDto createTestMovieDto(Long tmdbId) {
        return MovieDto.builder()
                .id("mongo123")
                .tmdbId(tmdbId)
                .title("Fight Club")
                .overview("An insomniac office worker looking for a way to change his life...")
                .posterPath("/poster.jpg")
                .backdropPath("/backdrop.jpg")
                .releaseDate(LocalDate.of(1999, 10, 15))
                .voteAverage(8.4)
                .voteCount(25000)
                .genres(Arrays.asList(
                        GenreDto.builder().id(18L).name("Drama").build(),
                        GenreDto.builder().id(53L).name("Thriller").build()
                ))
                .runtime(139)
                .status("Released")
                .budget(63000000L)
                .revenue(100853753L)
                .spokenLanguages(Arrays.asList("English"))
                .originalLanguage("en")
                .popularity(450.5)
                .adult(false)
                .imdbId("tt0137523")
                .tagline("Mischief. Mayhem. Soap.")
                .homepage("http://www.foxmovies.com/movies/fight-club")
                .build();
    }

    private PageResponse<MovieListDto> createTestPageResponse() {
        List<MovieListDto> movies = Arrays.asList(
                MovieListDto.builder()
                        .tmdbId(550L)
                        .title("Fight Club")
                        .overview("An insomniac office worker...")
                        .posterPath("/poster.jpg")
                        .releaseDate(LocalDate.of(1999, 10, 15))
                        .voteAverage(8.4)
                        .voteCount(25000)
                        .popularity(450.5)
                        .adult(false)
                        .build(),
                MovieListDto.builder()
                        .tmdbId(13L)
                        .title("Forrest Gump")
                        .overview("A man with a low IQ...")
                        .posterPath("/poster2.jpg")
                        .releaseDate(LocalDate.of(1994, 7, 6))
                        .voteAverage(8.8)
                        .voteCount(22000)
                        .popularity(320.3)
                        .adult(false)
                        .build()
        );

        return PageResponse.of(movies, 0, 20, 100L);
    }

    private List<VideoDto> createTestVideos() {
        return Arrays.asList(
                VideoDto.builder()
                        .id("video123")
                        .key("dQw4w9WgXcQ")
                        .name("Official Trailer")
                        .site("YouTube")
                        .size(1080)
                        .type("Trailer")
                        .official(true)
                        .publishedAt("2020-01-01T00:00:00Z")
                        .build(),
                VideoDto.builder()
                        .id("video456")
                        .key("abc123xyz")
                        .name("Behind the Scenes")
                        .site("YouTube")
                        .size(720)
                        .type("Featurette")
                        .official(true)
                        .publishedAt("2020-02-01T00:00:00Z")
                        .build()
        );
    }

    private CreditsDto createTestCredits(Long movieId) {
        List<CastDto> cast = Arrays.asList(
                CastDto.builder()
                        .id(287L)
                        .name("Brad Pitt")
                        .character("Tyler Durden")
                        .profilePath("/brad.jpg")
                        .order(0)
                        .build(),
                CastDto.builder()
                        .id(819L)
                        .name("Edward Norton")
                        .character("The Narrator")
                        .profilePath("/norton.jpg")
                        .order(1)
                        .build()
        );

        List<CrewDto> crew = Arrays.asList(
                CrewDto.builder()
                        .id(7467L)
                        .name("David Fincher")
                        .job("Director")
                        .department("Directing")
                        .profilePath("/fincher.jpg")
                        .build()
        );

        return CreditsDto.builder()
                .movieId(movieId)
                .cast(cast)
                .crew(crew)
                .build();
    }
}
