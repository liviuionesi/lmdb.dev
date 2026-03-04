package com.filmpire.movie.client;

import com.filmpire.movie.client.dto.TmdbMovieResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@WireMockTest(httpPort = 9999)
@DisplayName("TmdbClient and Rate Limiting Integration Tests")
class TmdbClientIntegrationTest {

    @Autowired
    private TmdbClient tmdbClient;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("tmdb.api.base-url", () -> "http://localhost:9999");
        registry.add("tmdb.api.key", () -> "test-api-key");
        registry.add("bucket4j.enabled", () -> "false");
    }

    @Test
    @DisplayName("Should fetch movie details successfully")
    void shouldFetchMovieDetails() {
        stubFor(get(urlPathEqualTo("/movie/550"))
                .withQueryParam("api_key", equalTo("test-api-key"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 550,
                                  "title": "Fight Club",
                                  "overview": "An insomniac office worker..."
                                }
                                """)));

        TmdbMovieResponse response = tmdbClient.getMovieDetails(550L, "test-api-key");
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(550L);
        assertThat(response.title()).isEqualTo("Fight Club");
    }

    @Test
    @DisplayName("Rate Limiter should throttle requests above 40 per 10s")
    void shouldThrottleRequestsWhenExceedingLimit() {
        stubFor(get(urlPathEqualTo("/movie/13"))
                .withQueryParam("api_key", equalTo("test-api-key"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 13, \"title\": \"Forrest Gump\"}")));

        Instant start = Instant.now();

        // Fire 42 synchronous requests. The first 40 should be instant.
        // The last 2 should block and delay execution, proving Bucket4J is working.
        IntStream.range(0, 42).forEach(i -> {
            tmdbClient.getMovieDetails(13L, "test-api-key");
        });

        Duration executionTime = Duration.between(start, Instant.now());
        
        // Since the limit is 40 per 10s, going over 40 must force at least some blocking
        // A single token trickles in every 250ms (10000ms / 40). 
        // Request 41 adds ~250ms, Request 42 adds ~250ms -> >500ms guaranteed delay
        assertThat(executionTime.toMillis()).isGreaterThan(200);
    }
}
