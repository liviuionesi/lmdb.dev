package com.filmpire.ai.client;

import com.filmpire.shared.dto.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Fetches recommendation candidates from movie-service's own persisted
 * catalog (via Eureka/{@code lb://}), never from TMDB directly — the point
 * of #36's acceptance criterion "recommendations are computed from
 * Filmpire's own catalog, not proxied from TMDB". movie-service's
 * {@code /api/v1/movies} controller already serves purely from its own
 * MongoDB/Redis-backed store (falling back to TMDB itself only on a cache
 * miss, per ADR-011) — this client is one hop further removed from TMDB
 * than that.
 */
@Component
@Slf4j
public class MovieCatalogClient {

    private final RestClient restClient;

    /**
     * @param restClientBuilder a {@code @LoadBalanced} builder (see
     *                          {@link com.filmpire.ai.config.RestClientConfig})
     *                          so {@code lb://movie-service} resolves via Eureka
     * @param movieServiceBaseUrl movie-service's base URL, {@code lb://movie-service} by default
     */
    public MovieCatalogClient(
        RestClient.Builder restClientBuilder,
        @Value("${movie-service.base-url}") String movieServiceBaseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(movieServiceBaseUrl).build();
    }

    /**
     * Fetches a page of currently popular movies as the candidate pool for
     * recommendations.
     *
     * @param count how many candidates to request
     * @return the candidate movies, or an empty list if movie-service is
     *         unreachable (recommendations degrade gracefully rather than
     *         failing the whole request)
     */
    public List<CandidateMovie> fetchCandidates(int count) {
        try {
            PageResponse<CandidateMovie> page = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/movies/popular")
                    .queryParam("page", 1)
                    .queryParam("size", count)
                    .build())
                .retrieve()
                .body(new ParameterizedTypeReference<PageResponse<CandidateMovie>>() {});
            return page == null || page.getContent() == null ? List.of() : page.getContent();
        } catch (Exception e) {
            log.warn("movie-service unreachable while fetching recommendation candidates: {}", e.getMessage());
            return List.of();
        }
    }
}
