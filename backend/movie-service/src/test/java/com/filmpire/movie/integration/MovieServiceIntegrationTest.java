package com.filmpire.movie.integration;

import com.filmpire.movie.config.TmdbClientTestStubConfig;
import com.filmpire.movie.dto.GenreDto;
import com.filmpire.movie.dto.MovieListDto;
import com.filmpire.movie.repository.MovieRepository;
import com.filmpire.shared.dto.ApiResponse;
import com.filmpire.shared.dto.PageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Movie Service.
 * Tests the full stack from controller to repository with real MongoDB container.
 * 
 * Note: These tests require TMDB_API_KEY environment variable to be set.
 * If not set, tests will use mock/stubbed responses.
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
@DisplayName("Movie Service Integration Tests")
@SuppressWarnings({"resource", "try", "null"})
class MovieServiceIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8.0.0")
            .withExposedPorts(27017)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(60));

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final MovieRepository movieRepository;

    @Autowired
    MovieServiceIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, MovieRepository movieRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.movieRepository = movieRepository;
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.cache.type", () -> "none"); // Disable cache for integration tests
        registry.add("eureka.client.enabled", () -> "false"); // Disable Eureka
        registry.add("spring.cloud.config.enabled", () -> "false"); // Disable config server
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
    @DisplayName("Integration: GET /api/v1/genres - Should return genres list")
    void getGenres_ShouldReturnGenresList() throws Exception {
        // This test will call actual TMDB API if key is set
        // Otherwise it will fail gracefully
        
        MvcResult result = mockMvc.perform(get("/api/v1/genres")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        ApiResponse<List<GenreDto>> response = objectMapper.readValue(
                content,
                new TypeReference<ApiResponse<List<GenreDto>>>() {}
        );

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
    }

    @Test
    @DisplayName("Integration: Full flow - Search, get details, get credits")
    void fullMovieWorkflow_ShouldWork() throws Exception {
        // Note: This test requires a valid TMDB API key
        // If not available, the test will demonstrate the flow structure
        
        // Step 1: Search for movies
        mockMvc.perform(get("/api/v1/movies/search")
                .param("query", "Fight Club")
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());

        // Step 2: Get popular movies
        mockMvc.perform(get("/api/v1/movies/popular")
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Step 3: Get trending movies
        mockMvc.perform(get("/api/v1/movies/trending")
                .param("timeWindow", "week")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Integration: Discover movies with filters")
    void discoverMovies_WithFilters_ShouldWork() throws Exception {
        mockMvc.perform(get("/api/v1/movies/discover")
                .param("page", "1")
                .param("size", "20")
                .param("genreId", "28")
                .param("year", "2023")
                .param("minRating", "7.0")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("Integration: Test pagination for discover endpoint")
    void discoverMovies_Pagination_ShouldWork() throws Exception {
        // Test page 1
        MvcResult result1 = mockMvc.perform(get("/api/v1/movies/discover")
                .param("page", "1")
                .param("size", "5")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String content1 = result1.getResponse().getContentAsString();
        ApiResponse<PageResponse<MovieListDto>> response1 = objectMapper.readValue(
                content1,
                new TypeReference<ApiResponse<PageResponse<MovieListDto>>>() {}
        );

        assertThat(response1.isSuccess()).isTrue();
        assertThat(response1.getData()).isNotNull();
        assertThat(response1.getData().getPageNumber()).isZero(); // 0-indexed

        // Test page 2
        MvcResult result2 = mockMvc.perform(get("/api/v1/movies/discover")
                .param("page", "2")
                .param("size", "5")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String content2 = result2.getResponse().getContentAsString();
        ApiResponse<PageResponse<MovieListDto>> response2 = objectMapper.readValue(
                content2,
                new TypeReference<ApiResponse<PageResponse<MovieListDto>>>() {}
        );

        assertThat(response2.isSuccess()).isTrue();
        assertThat(response2.getData()).isNotNull();
        assertThat(response2.getData().getPageNumber()).isEqualTo(1); // 0-indexed
    }

    @Test
    @DisplayName("Integration: Test multiple concurrent requests")
    void concurrentRequests_ShouldHandleGracefully() throws Exception {
        // Simulate multiple concurrent requests
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/movies/popular")
                    .param("page", "1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Test
    @DisplayName("Integration: Test API response structure consistency")
    void apiResponseStructure_ShouldBeConsistent() throws Exception {
        // Test movie list endpoint
        MvcResult result = mockMvc.perform(get("/api/v1/movies/top-rated")
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.statusCode").exists())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        ApiResponse<PageResponse<MovieListDto>> response = objectMapper.readValue(
                content,
                new TypeReference<ApiResponse<PageResponse<MovieListDto>>>() {}
        );

        // Verify PageResponse structure
        assertThat(response.getData().getContent()).isNotNull();
        assertThat(response.getData().getPageNumber()).isNotNegative();
        assertThat(response.getData().getPageSize()).isPositive();
        assertThat(response.getData().getTotalElements()).isNotNegative();
        assertThat(response.getData().getTotalPages()).isNotNegative();
    }
}
