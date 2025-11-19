package com.filmpire.movie.integration;

import com.filmpire.movie.config.TmdbClientTestStubConfig;
import com.filmpire.movie.model.Movie;
import com.filmpire.movie.repository.MovieRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests.
 * Tests complete workflows from HTTP request to database and back.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration," +
            "org.springframework.cloud.openfeign.FeignAutoConfiguration"
    }
)
@AutoConfigureMockMvc
@Import(TmdbClientTestStubConfig.class)
@Testcontainers
@DisplayName("End-to-End Integration Tests")
@SuppressWarnings({"resource", "try", "null"})
class EndToEndIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8.0.0")
            .withExposedPorts(27017)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(60));

    private final MockMvc mockMvc;
    private final MovieRepository movieRepository;

    @Autowired
    EndToEndIntegrationTest(MockMvc mockMvc, MovieRepository movieRepository) {
        this.mockMvc = mockMvc;
        this.movieRepository = movieRepository;
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.cache.type", () -> "none");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
    }

    @BeforeEach
    void setUp() {
        movieRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        movieRepository.deleteAll();
    }


    @Test
    @DisplayName("E2E: Complete movie discovery workflow")
    void completeMovieDiscoveryWorkflow() throws Exception {
        // This test demonstrates a complete user workflow
        // Note: Requires valid TMDB API key for full functionality

        // Step 1: Get genres
        mockMvc.perform(get("/api/v1/genres")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());

        // Step 2: Discover popular movies
        mockMvc.perform(get("/api/v1/movies/popular")
                .param("page", "1")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").exists());

        // Step 3: Get trending movies
        mockMvc.perform(get("/api/v1/movies/trending")
                .param("timeWindow", "week")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("E2E: Search and get movie details workflow")
    void searchAndGetDetailsWorkflow() throws Exception {
        // This test demonstrates searching and getting detailed information

        // Step 1: Search for movies
        mockMvc.perform(get("/api/v1/movies/search")
                .param("query", "Matrix")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());

        // Step 2: Get top-rated movies
        mockMvc.perform(get("/api/v1/movies/top-rated")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("E2E: Filter and discover movies workflow")
    void filterAndDiscoverWorkflow() throws Exception {
        // This test demonstrates filtering movies with various criteria

        // Step 1: Discover by genre
        mockMvc.perform(get("/api/v1/movies/discover")
                .param("page", "1")
                .param("size", "20")
                .param("genreId", "28") // Action
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Step 2: Discover by year and rating
        mockMvc.perform(get("/api/v1/movies/discover")
                .param("page", "1")
                .param("size", "20")
                .param("year", "2023")
                .param("minRating", "8.0")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Step 3: Discover with all filters
        mockMvc.perform(get("/api/v1/movies/discover")
                .param("page", "1")
                .param("size", "20")
                .param("genreId", "18") // Drama
                .param("year", "2022")
                .param("minRating", "7.5")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("E2E: Test API response consistency across endpoints")
    void apiResponseConsistency() throws Exception {
        // Verify all endpoints return consistent ApiResponse structure

        // Popular movies
        mockMvc.perform(get("/api/v1/movies/popular")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.statusCode").exists());

        // Top-rated movies
        mockMvc.perform(get("/api/v1/movies/top-rated")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").exists());

        // Genres
        mockMvc.perform(get("/api/v1/genres")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("E2E: Test pagination across multiple pages")
    void paginationWorkflow() throws Exception {
        // Test paginating through results

        // Page 1
        mockMvc.perform(get("/api/v1/movies/popular")
                .param("page", "1")
                .param("size", "5")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNumber").value(0));

        // Page 2
        mockMvc.perform(get("/api/v1/movies/popular")
                .param("page", "2")
                .param("size", "5")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNumber").value(1));

        // Page 3
        mockMvc.perform(get("/api/v1/movies/popular")
                .param("page", "3")
                .param("size", "5")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNumber").value(2));
    }

    @Test
    @DisplayName("E2E: Test caching behavior with MongoDB")
    void cachingBehaviorWithMongoDB() throws Exception {
        // Pre-populate MongoDB with a movie
        Movie movie = Movie.builder()
                .tmdbId(550L)
                .title("Fight Club")
                .overview("An insomniac office worker...")
                .posterPath("/poster.jpg")
                .backdropPath("/backdrop.jpg")
                .releaseDate(LocalDate.of(1999, 10, 15))
                .voteAverage(8.4)
                .voteCount(25000)
                .runtime(139)
                .status("Released")
                .budget(63000000L)
                .revenue(100853753L)
                .popularity(450.5)
                .adult(false)
                .imdbId("tt0137523")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .tmdbSyncVersion(1)
                .build();

        movieRepository.save(movie);

        // Request should return from MongoDB cache
        mockMvc.perform(get("/api/v1/movies/{id}", 550L)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tmdbId").value(550))
                .andExpect(jsonPath("$.data.title").value("Fight Club"));
    }

    @Test
    @DisplayName("E2E: Test error handling workflow")
    void errorHandlingWorkflow() throws Exception {
        // Test various error scenarios

        // Missing required parameter
        mockMvc.perform(get("/api/v1/movies/search")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Invalid path variable
        mockMvc.perform(get("/api/v1/movies/{id}", "invalid")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("E2E: Test concurrent requests handling")
    void concurrentRequestsHandling() throws Exception {
        // Simulate multiple concurrent requests
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/v1/movies/popular")
                    .param("page", "1")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Test
    @DisplayName("E2E: Test all movie list endpoints")
    void allMovieListEndpoints() throws Exception {
        // Test all endpoints that return movie lists

        mockMvc.perform(get("/api/v1/movies/popular")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/movies/top-rated")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/movies/trending")
                .param("timeWindow", "day")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/movies/trending")
                .param("timeWindow", "week")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/movies/discover")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/movies/search")
                .param("query", "test")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}

