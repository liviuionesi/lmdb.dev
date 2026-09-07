package dev.lmdb.movie.facade;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lmdb.movie.client.dto.TmdbGenresResponse;
import dev.lmdb.movie.client.dto.TmdbMovieListResponse;
import dev.lmdb.movie.event.TmdbEventProducer;
import dev.lmdb.movie.model.Movie;
import dev.lmdb.movie.service.MovieService;
import dev.lmdb.movie.support.TestCacheConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for {@link TmdbFacadeController} ({@code @WebMvcTest}: only the MVC slice loads,
 * {@link MovieService} and {@link TmdbEventProducer} are {@code @MockitoBean}s).
 *
 * <p>Story #96 / Task #40 require every save-through endpoint to publish a {@code
 * tmdb.document.saved} event. {@link dev.lmdb.movie.event.TmdbEventProducerIntegrationTest} proves
 * the producer itself delivers to Kafka; this class proves the controller actually calls it, once
 * per endpoint, so the two together cover the full "every save-through publishes" claim.
 */
@WebMvcTest(value = TmdbFacadeController.class, properties = "spring.cloud.config.enabled=false")
@Import(TestCacheConfig.class)
@DisplayName("TmdbFacadeController Kafka event-publishing Tests")
class TmdbFacadeControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MovieService movieService;

  @MockitoBean private TmdbEventProducer eventProducer;

  /**
   * {@code GET /genre/movie/list} must publish one event per call.
   *
   * @throws Exception if the mock request fails
   */
  @Test
  @DisplayName("GET /genre/movie/list publishes a save-through event")
  void genreList_PublishesEvent() throws Exception {
    when(movieService.getGenresRaw()).thenReturn(new TmdbGenresResponse(List.of()));

    mockMvc.perform(get("/genre/movie/list")).andExpect(status().isOk());

    verify(eventProducer)
        .publishDocumentSavedEvent("genre:list", "GENRE_LIST", "/genre/movie/list");
  }

  /**
   * {@code GET /movie/{category}} (a fixed TMDB category name) must publish one event per call.
   *
   * @throws Exception if the mock request fails
   */
  @Test
  @DisplayName("GET /movie/{category} publishes a save-through event")
  void movieByCategory_PublishesEvent() throws Exception {
    when(movieService.getMovieCategoryRaw("popular", 1))
        .thenReturn(new TmdbMovieListResponse(1, 1, 0, List.of()));

    mockMvc.perform(get("/movie/popular")).andExpect(status().isOk());

    verify(eventProducer)
        .publishDocumentSavedEvent("category:popular:page:1", "MOVIE_CATEGORY", "/movie/popular");
  }

  /**
   * {@code GET /movie/{id}} (a numeric TMDB movie id) must publish one event per call.
   *
   * @throws Exception if the mock request fails
   */
  @Test
  @DisplayName("GET /movie/{id} publishes a save-through event")
  void movieById_PublishesEvent() throws Exception {
    when(movieService.getMovieForFacade(anyLong(), anySet())).thenReturn(Movie.builder().build());

    mockMvc.perform(get("/movie/550")).andExpect(status().isOk());

    verify(eventProducer).publishDocumentSavedEvent("movie:550", "MOVIE_DETAIL", "/movie/550");
  }

  /**
   * {@code GET /movie/{id}/recommendations} must publish one event per call.
   *
   * @throws Exception if the mock request fails
   */
  @Test
  @DisplayName("GET /movie/{id}/recommendations publishes a save-through event")
  void recommendations_PublishesEvent() throws Exception {
    when(movieService.getRecommendedMoviesRaw(550L, 1))
        .thenReturn(new TmdbMovieListResponse(1, 1, 0, List.of()));

    mockMvc.perform(get("/movie/550/recommendations")).andExpect(status().isOk());

    verify(eventProducer)
        .publishDocumentSavedEvent(
            "recommendations:550:page:1", "RECOMMENDATIONS", "/movie/550/recommendations");
  }

  /**
   * {@code GET /movie/{id}/similar} must publish one event per call.
   *
   * @throws Exception if the mock request fails
   */
  @Test
  @DisplayName("GET /movie/{id}/similar publishes a save-through event")
  void similar_PublishesEvent() throws Exception {
    when(movieService.getSimilarMoviesRaw(550L, 1))
        .thenReturn(new TmdbMovieListResponse(1, 1, 0, List.of()));

    mockMvc.perform(get("/movie/550/similar")).andExpect(status().isOk());

    verify(eventProducer)
        .publishDocumentSavedEvent("similar:550:page:1", "SIMILAR", "/movie/550/similar");
  }

  /**
   * {@code GET /discover/movie} must publish one event per call.
   *
   * @throws Exception if the mock request fails
   */
  @Test
  @DisplayName("GET /discover/movie publishes a save-through event")
  void discover_PublishesEvent() throws Exception {
    when(movieService.discoverMoviesRaw(anyInt(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new TmdbMovieListResponse(1, 1, 0, List.of()));

    mockMvc.perform(get("/discover/movie")).andExpect(status().isOk());

    verify(eventProducer)
        .publishDocumentSavedEvent("discover:page:1", "DISCOVER", "/discover/movie");
  }

  /**
   * {@code GET /search/movie} must publish one event per call.
   *
   * @throws Exception if the mock request fails
   */
  @Test
  @DisplayName("GET /search/movie publishes a save-through event")
  void search_PublishesEvent() throws Exception {
    when(movieService.searchMoviesRaw(anyString(), anyInt()))
        .thenReturn(new TmdbMovieListResponse(1, 1, 0, List.of()));

    mockMvc
        .perform(
            get("/search/movie")
                .param("query", "fight club")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    verify(eventProducer)
        .publishDocumentSavedEvent("search:query:fight club:page:1", "SEARCH", "/search/movie");
  }
}
