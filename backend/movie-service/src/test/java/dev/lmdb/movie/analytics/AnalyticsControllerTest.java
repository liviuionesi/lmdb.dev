package dev.lmdb.movie.analytics;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lmdb.movie.support.TestCacheConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for {@link AnalyticsController} ({@code @WebMvcTest}: only the MVC slice loads,
 * {@link RequestCountRepository} is a {@code @MockitoBean}).
 *
 * <p>Story #96 / Task #41 require {@code GET /api/v1/analytics/most-requested} to return the
 * aggregated counts. {@link TmdbAnalyticsConsumerIntegrationTest} proves the counts are aggregated
 * correctly in MongoDB, but calls {@link RequestCountRepository} directly rather than the HTTP
 * endpoint. This class closes that gap by exercising the endpoint itself.
 */
@WebMvcTest(value = AnalyticsController.class, properties = "spring.cloud.config.enabled=false")
@Import(TestCacheConfig.class)
@DisplayName("AnalyticsController Tests")
class AnalyticsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RequestCountRepository requestCountRepository;

  /**
   * Given two counters in descending count order, when the endpoint is called without a {@code
   * limit}, then it returns both, ordered as the repository returned them, and defaults the page
   * size to 10.
   *
   * @throws Exception if the mock request fails
   */
  @Test
  @DisplayName("GET /api/v1/analytics/most-requested - returns counts ordered by count descending")
  void getMostRequested_ReturnsAggregatedCounts() throws Exception {
    RequestCount top = new RequestCount("movie:550", "MOVIE_DETAIL", 42L, Instant.now());
    RequestCount second =
        new RequestCount("category:popular:page:1", "MOVIE_CATEGORY", 7L, Instant.now());
    when(requestCountRepository.findAllByOrderByCountDesc(any(Pageable.class)))
        .thenReturn(List.of(top, second));

    mockMvc
        .perform(get("/api/v1/analytics/most-requested"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].key").value("movie:550"))
        .andExpect(jsonPath("$[0].count").value(42))
        .andExpect(jsonPath("$[1].key").value("category:popular:page:1"));

    verify(requestCountRepository).findAllByOrderByCountDesc(PageRequest.of(0, 10));
  }

  /**
   * Given a {@code limit} query parameter, when the endpoint is called, then the repository is
   * queried with that page size, so the response is bounded exactly as requested.
   *
   * @throws Exception if the mock request fails
   */
  @Test
  @DisplayName("GET /api/v1/analytics/most-requested - honors the limit query parameter")
  void getMostRequested_HonorsLimitParameter() throws Exception {
    when(requestCountRepository.findAllByOrderByCountDesc(any(Pageable.class)))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/analytics/most-requested").param("limit", "3"))
        .andExpect(status().isOk());

    verify(requestCountRepository).findAllByOrderByCountDesc(PageRequest.of(0, 3));
  }

  /**
   * Given a {@code limit} above the 50-entry cap, when the endpoint is called, then the repository
   * is queried with the capped page size, not the raw caller-supplied value — proving the endpoint
   * cannot be made to return an unbounded result set.
   *
   * @throws Exception if the mock request fails
   */
  @Test
  @DisplayName("GET /api/v1/analytics/most-requested - caps limit at 50")
  void getMostRequested_CapsLimitAtFifty() throws Exception {
    when(requestCountRepository.findAllByOrderByCountDesc(any(Pageable.class)))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/analytics/most-requested").param("limit", "500"))
        .andExpect(status().isOk());

    verify(requestCountRepository).findAllByOrderByCountDesc(PageRequest.of(0, 50));
  }
}
