package dev.lmdb.actor.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lmdb.actor.dto.ActorDtos.ActorDto;
import dev.lmdb.actor.dto.ActorDtos.ActorImageDto;
import dev.lmdb.actor.dto.ActorDtos.ActorSearchResponse;
import dev.lmdb.actor.dto.ActorDtos.ActorSummaryDto;
import dev.lmdb.actor.dto.ActorDtos.CrewCreditDto;
import dev.lmdb.actor.dto.ActorDtos.FilmographyEntryDto;
import dev.lmdb.actor.dto.ActorDtos.FilmographyPageDto;
import dev.lmdb.actor.service.ActorService;
import dev.lmdb.shared.exception.ResourceNotFoundException;
import dev.lmdb.shared.exception.ServiceUnavailableException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * WebMvc unit tests for {@link ActorController} and {@link GlobalExceptionHandler}.
 *
 * <p>Uses Spring's {@code @WebMvcTest} slice to test HTTP request routing, status code mapping,
 * JSON response payload generation, HATEOAS link creation, and global exception translation without
 * spinning up a full application context or external dependencies.
 */
@WebMvcTest(
    controllers = {ActorController.class, GlobalExceptionHandler.class},
    properties = "spring.cloud.config.enabled=false")
@DisplayName("ActorController & GlobalExceptionHandler WebMvc Tests")
class ActorControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ActorService actorService;

  @MockitoBean private CacheManager cacheManager;

  /**
   * Helper factory creating a fully populated {@link ActorDto} instance for test scenarios.
   *
   * @return sample actor DTO
   */
  private ActorDto sampleActor() {
    return new ActorDto(
        819L,
        "Edward Norton",
        "Edward Harrison Norton is an American actor.",
        LocalDate.of(1969, Month.AUGUST, 18),
        "Boston, Massachusetts, USA",
        "/profile.jpg",
        28.5,
        List.of("Edward Harrison Norton"),
        "Acting",
        2,
        "nm0001570",
        "https://edwardnorton.org",
        false);
  }

  /**
   * Verifies that {@code GET /api/v1/actors/{id}} returns 200 OK with the actor details wrapped in
   * an {@code ApiResponse} envelope along with HATEOAS self, movies, and images hyperlinks.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GET /api/v1/actors/{id}: returns 200 with actor profile and HATEOAS links")
  void getActorReturnsProfileWithLinks() throws Exception {
    // Given
    when(actorService.getActor(819L)).thenReturn(sampleActor());

    // When & Then
    mockMvc
        .perform(get("/api/v1/actors/819").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.statusCode").value(200))
        .andExpect(jsonPath("$.data.tmdbId").value(819))
        .andExpect(jsonPath("$.data.name").value("Edward Norton"))
        .andExpect(jsonPath("$.data._links.self.href", containsString("/api/v1/actors/819")))
        .andExpect(
            jsonPath("$.data._links.movies.href", containsString("/api/v1/actors/819/movies")))
        .andExpect(
            jsonPath("$.data._links.images.href", containsString("/api/v1/actors/819/images")));
  }

  /**
   * Verifies that {@code GET /api/v1/actors/{id}/movies} returns 200 OK with the paginated
   * filmography list and pagination metadata.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GET /api/v1/actors/{id}/movies: returns paginated filmography")
  void getActorMoviesReturnsPaginatedFilmography() throws Exception {
    // Given
    FilmographyEntryDto entry =
        new FilmographyEntryDto(
            550L, "Fight Club", "The Narrator", "1999-10-15", "/poster.jpg", 8.4);
    FilmographyPageDto pageDto = new FilmographyPageDto(1, 1, 1, List.of(entry));
    when(actorService.getFilmographyPage(819L, 1, 20)).thenReturn(pageDto);

    // When & Then
    mockMvc
        .perform(get("/api/v1/actors/819/movies").param("page", "1").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.totalItems").value(1))
        .andExpect(jsonPath("$.data.results[0].movieId").value(550));
  }

  /**
   * Verifies that {@code GET /api/v1/actors/{id}/crew} returns 200 OK with the actor's crew credits
   * and forwards the optional department/job query params through to the service unchanged (#217).
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName(
      "GET /api/v1/actors/{id}/crew: returns crew credits, forwards department/job filters")
  void getCrewCreditsReturnsCredits() throws Exception {
    // Given
    CrewCreditDto credit =
        new CrewCreditDto(
            155L, "The Dark Knight", "Director", "Directing", "2008-07-18", "/poster.jpg", 8.5);
    when(actorService.getCrewCredits(819L, "Directing", "Director")).thenReturn(List.of(credit));

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/actors/819/crew")
                .param("department", "Directing")
                .param("job", "Director"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].movieId").value(155))
        .andExpect(jsonPath("$.data[0].job").value("Director"))
        .andExpect(jsonPath("$.data[0].department").value("Directing"));
  }

  /**
   * Verifies that {@code GET /api/v1/actors/{id}/images} returns 200 OK with the actor's profile
   * image collection.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GET /api/v1/actors/{id}/images: returns actor profile images")
  void getActorImagesReturnsImages() throws Exception {
    // Given
    ActorImageDto img = new ActorImageDto("/one.jpg", 0.667, 1500, 1000, "en", 6.0, 10);
    when(actorService.getImages(819L)).thenReturn(List.of(img));

    // When & Then
    mockMvc
        .perform(get("/api/v1/actors/819/images"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].filePath").value("/one.jpg"))
        .andExpect(jsonPath("$.data[0].voteCount").value(10));
  }

  /**
   * Verifies that {@code GET /api/v1/actors/popular} returns 200 OK with popular actors.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GET /api/v1/actors/popular: returns popular actors")
  void getPopularReturnsActors() throws Exception {
    // Given
    ActorSummaryDto summary = new ActorSummaryDto(976L, "Jason Statham", "/j.jpg", 180.0);
    ActorSearchResponse response = new ActorSearchResponse(1, 10, 200, List.of(summary));
    when(actorService.getPopular(1)).thenReturn(response);

    // When & Then
    mockMvc
        .perform(get("/api/v1/actors/popular").param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.results[0].name").value("Jason Statham"));
  }

  /**
   * Verifies that {@code GET /api/v1/actors/search} returns 200 OK with matching actor summaries.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GET /api/v1/actors/search: returns search results")
  void searchReturnsResults() throws Exception {
    // Given
    ActorSummaryDto summary = new ActorSummaryDto(1245L, "Léa Seydoux", "/l.jpg", 15.0);
    ActorSearchResponse response = new ActorSearchResponse(1, 1, 1, List.of(summary));
    when(actorService.search("seydoux", 1)).thenReturn(response);

    // When & Then
    mockMvc
        .perform(get("/api/v1/actors/search").param("query", "seydoux").param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.results[0].name").value("Léa Seydoux"));
  }

  /**
   * Verifies that {@link GlobalExceptionHandler} intercepts {@link RestClientResponseException} and
   * translates it into an {@code ApiResponse} error envelope echoing the upstream HTTP status.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName(
      "GlobalExceptionHandler: maps RestClientResponseException to upstream status envelope")
  void handlesRestClientResponseException() throws Exception {
    // Given
    when(actorService.getActor(999L))
        .thenThrow(
            HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                HttpHeaders.EMPTY,
                "{\"status_code\":34}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

    // When & Then
    mockMvc
        .perform(get("/api/v1/actors/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.statusCode").value(404));
  }

  /**
   * Verifies that {@link GlobalExceptionHandler} intercepts {@link ResourceAccessException} and
   * translates network timeouts or socket errors into HTTP 503 Service Unavailable.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GlobalExceptionHandler: maps ResourceAccessException to 503 envelope")
  void handlesResourceAccessException() throws Exception {
    // Given
    when(actorService.getActor(819L))
        .thenThrow(new ResourceAccessException("Connection timed out"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/actors/819"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.statusCode").value(503));
  }

  /**
   * Verifies that {@link GlobalExceptionHandler} translates {@link ServiceUnavailableException}
   * into HTTP 503 Service Unavailable.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GlobalExceptionHandler: maps ServiceUnavailableException to 503 envelope")
  void handlesServiceUnavailableException() throws Exception {
    // Given
    when(actorService.getActor(819L))
        .thenThrow(new ServiceUnavailableException("Downstream outage"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/actors/819"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.statusCode").value(503));
  }

  /**
   * Verifies that {@link GlobalExceptionHandler} translates {@link ResourceNotFoundException} into
   * HTTP 404 Not Found preserving the exception message.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GlobalExceptionHandler: maps ResourceNotFoundException to 404 envelope")
  void handlesResourceNotFoundException() throws Exception {
    // Given
    when(actorService.getActor(819L))
        .thenThrow(new ResourceNotFoundException("Actor not found locally"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/actors/819"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Actor not found locally"));
  }

  /**
   * Verifies that {@link GlobalExceptionHandler} translates unanticipated runtime exceptions into a
   * sanitized HTTP 500 Internal Server Error envelope without leaking internal details.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GlobalExceptionHandler: maps unexpected Exception to 500 envelope")
  void handlesUnexpectedException() throws Exception {
    // Given
    when(actorService.getActor(819L))
        .thenThrow(new RuntimeException("Unexpected internal failure"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/actors/819"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
  }
}
