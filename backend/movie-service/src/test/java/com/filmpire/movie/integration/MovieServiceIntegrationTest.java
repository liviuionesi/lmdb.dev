package com.filmpire.movie.integration;

import com.filmpire.movie.config.TmdbClientTestStubConfig;
import com.filmpire.movie.dto.GenreDto;
import com.filmpire.movie.dto.MovieListDto;
import com.filmpire.movie.repository.MovieRepository;
import com.filmpire.shared.dto.PageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
 * Full-stack native-API integration tests: real controller → service →
 * MongoDB (Testcontainers), with the TMDB client replaced by
 * {@link TmdbClientTestStubConfig} so no real network call is made and results
 * are deterministic.
 *
 * <p>These exercise the {@code /api/v1} endpoints end to end, focusing on the
 * plumbing that the isolated slice tests can't prove: real JSON
 * serialization/deserialization round-trips (via ObjectMapper +
 * PageResponse), pagination metadata correctness, and response-envelope
 * consistency across endpoints. Because the TMDB client is stubbed, assertions
 * are on structure/shape rather than specific movie data.</p>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc
@Import(TmdbClientTestStubConfig.class)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Movie Service Integration Tests")
class MovieServiceIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8.0.0")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(60));

    /**
     * Points Spring Data at the container's Mongo.
     *
     * @param registry Spring test property registry
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final MovieRepository movieRepository;

    /**
     * Constructor injection of the web client, JSON mapper and repository.
     *
     * @param mockMvc         MVC test client
     * @param objectMapper    JSON mapper for response deserialization
     * @param movieRepository repository, used to reset state between tests
     */
    @org.springframework.beans.factory.annotation.Autowired
    MovieServiceIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, MovieRepository movieRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.movieRepository = movieRepository;
    }

    /** Stops the container after the class. */
    @AfterAll
    static void cleanup() {
        if (mongoDBContainer != null && mongoDBContainer.isRunning()) {
            mongoDBContainer.stop();
        }
    }

    /** Empties the collection before each test for deterministic state. */
    @BeforeEach
    void setUp() {
        movieRepository.deleteAll();
    }

    /** Empties the collection after each test. */
    @AfterEach
    void tearDown() {
        movieRepository.deleteAll();
    }

    /**
     * The genres endpoint must return a non-empty JSON array that
     * deserializes back into GenreDtos — proves the serialization round-trip
     * works over the real HTTP stack, not just in a slice.
     *
     * @throws Exception if the request or JSON parse fails
     */
    @Test
    @DisplayName("Integration: GET /api/v1/genres - Should return genres list")
    void getGenres_ShouldReturnGenresList() throws Exception {
        // This test will call actual TMDB API if key is set
        // Otherwise it will fail gracefully
        
        MvcResult result = mockMvc.perform(get("/api/v1/genres")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").exists())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        List<GenreDto> genres = objectMapper.readValue(
                content,
                new TypeReference<List<GenreDto>>() {}
        );

        assertThat(genres).isNotNull().isNotEmpty();
    }

    /**
     * Exercises several list endpoints in sequence (search → popular →
     * trending) to prove they all wire up and return the paged content shape
     * over the full stack — a smoke test of the primary browse paths.
     *
     * @throws Exception if any request fails
     */
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
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").exists());

        // Step 2: Get popular movies
        mockMvc.perform(get("/api/v1/movies/popular")
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        // Step 3: Get trending movies
        mockMvc.perform(get("/api/v1/movies/trending")
                .param("timeWindow", "week")
                .param("page", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    /**
     * Discover must accept all filter params together (genre, year, minRating)
     * over the real stack and return the paged content shape — confirms
     * multi-param binding survives end to end, not just in the slice test.
     *
     * @throws Exception if the request fails
     */
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
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").exists());
    }

    /**
     * Requesting page 1 then page 2 must deserialize to PageResponses whose
     * pageNumber is 0 then 1 respectively — proves the 1-based request page maps
     * to the 0-based response index consistently, the subtle off-by-one that
     * breaks pagers if wrong.
     *
     * @throws Exception if a request or JSON parse fails
     */
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
        PageResponse<MovieListDto> response1 = objectMapper.readValue(
                content1,
                new TypeReference<PageResponse<MovieListDto>>() {}
        );

        assertThat(response1).isNotNull();
        assertThat(response1.getContent()).isNotNull();
        assertThat(response1.getPageNumber()).isZero(); // 0-indexed

        // Test page 2
        MvcResult result2 = mockMvc.perform(get("/api/v1/movies/discover")
                .param("page", "2")
                .param("size", "5")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String content2 = result2.getResponse().getContentAsString();
        PageResponse<MovieListDto> response2 = objectMapper.readValue(
                content2,
                new TypeReference<PageResponse<MovieListDto>>() {}
        );

        assertThat(response2).isNotNull();
        assertThat(response2.getContent()).isNotNull();
        assertThat(response2.getPageNumber()).isEqualTo(1); // 0-indexed
    }

    /**
     * Firing several requests at the same endpoint in succession must all
     * return 200 with the expected shape — a basic stability check that the
     * full stack (incl. the shared stubbed client and Mongo connection) handles
     * repeated load without erroring. (Sequential, not truly parallel.)
     *
     * @throws Exception if any request fails
     */
    @Test
    @DisplayName("Integration: Test multiple concurrent requests")
    void concurrentRequests_ShouldHandleGracefully() throws Exception {
        // Simulate multiple concurrent requests
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/movies/popular")
                    .param("page", "1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    /**
     * The list endpoints must all return the same PageResponse envelope —
     * content array plus non-negative pageNumber/pageSize/totalElements/
     * totalPages — so clients can rely on one consistent pagination contract
     * across every catalog endpoint.
     *
     * @throws Exception if the request or JSON parse fails
     */
    @Test
    @DisplayName("Integration: Test API response structure consistency")
    void apiResponseStructure_ShouldBeConsistent() throws Exception {
        // Test movie list endpoint
        MvcResult result = mockMvc.perform(get("/api/v1/movies/top-rated")
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.pageNumber").exists())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        PageResponse<MovieListDto> response = objectMapper.readValue(
                content,
                new TypeReference<PageResponse<MovieListDto>>() {}
        );

        // Verify PageResponse structure
        assertThat(response.getContent()).isNotNull();
        assertThat(response.getPageNumber()).isNotNegative();
        assertThat(response.getPageSize()).isPositive();
        assertThat(response.getTotalElements()).isNotNegative();
        assertThat(response.getTotalPages()).isNotNegative();
    }
}
