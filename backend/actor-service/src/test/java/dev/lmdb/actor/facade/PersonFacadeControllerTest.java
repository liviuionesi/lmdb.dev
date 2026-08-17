package dev.lmdb.actor.facade;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lmdb.actor.client.dto.TmdbPersonImagesResponse.TmdbProfileImage;
import dev.lmdb.actor.client.dto.TmdbPersonMovieCreditsResponse;
import dev.lmdb.actor.client.dto.TmdbPersonMovieCreditsResponse.TmdbCastCredit;
import dev.lmdb.actor.client.dto.TmdbPersonResponse;
import dev.lmdb.actor.client.dto.TmdbPersonSearchResponse;
import dev.lmdb.actor.client.dto.TmdbPersonSearchResponse.TmdbPersonSummary;
import dev.lmdb.actor.mapper.ActorMapper;
import dev.lmdb.actor.model.Actor;
import dev.lmdb.actor.model.ActorProfileImage;
import dev.lmdb.actor.service.ActorService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * WebMvc unit tests for {@link PersonFacadeController}.
 *
 * <p>Validates TMDB v3 compatibility endpoints for the LMDB frontend without requiring a live
 * database or external TMDB API. Verifies JSON serialization matching TMDB specifications,
 * client-side non-numeric ID rejection, and upstream error forwarding.
 */
@WebMvcTest(controllers = PersonFacadeController.class)
@DisplayName("PersonFacadeController WebMvc Tests")
class PersonFacadeControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ActorService actorService;

  @MockitoBean private ActorMapper actorMapper;

  @MockitoBean private CacheManager cacheManager;

  /**
   * Helper factory creating a sample {@link Actor} domain entity.
   *
   * @return sample actor entity
   */
  private Actor sampleActor() {
    return Actor.builder()
        .tmdbId(819L)
        .name("Edward Norton")
        .biography("Edward Harrison Norton...")
        .birthDate(LocalDate.of(1969, Month.AUGUST, 18))
        .birthPlace("Boston, Massachusetts, USA")
        .profilePath("/e.jpg")
        .popularity(9.1)
        .alsoKnownAs(List.of("Edward Harrison Norton"))
        .knownForDepartment("Acting")
        .gender(2)
        .imdbId("nm0001570")
        .homepage(null)
        .adult(false)
        .build();
  }

  /**
   * Helper factory creating a sample {@link TmdbPersonResponse} facade record.
   *
   * @return sample TMDB person response
   */
  private TmdbPersonResponse sampleTmdbResponse() {
    return new TmdbPersonResponse(
        819L,
        "Edward Norton",
        "Edward Harrison Norton...",
        LocalDate.of(1969, Month.AUGUST, 18),
        "Boston, Massachusetts, USA",
        "/e.jpg",
        9.1,
        List.of("Edward Harrison Norton"),
        "Acting",
        2,
        "nm0001570",
        null,
        false);
  }

  /**
   * Verifies that {@code GET /person/{id}} returns 200 OK with the TMDB-compatible person detail
   * JSON envelope containing exact snake_case fields.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GET /person/{id}: returns 200 with TMDB person shape")
  void personDetailsReturnsTmdbShape() throws Exception {
    // Given
    Actor actor = sampleActor();
    when(actorService.getOrFetchActorEntity(819L)).thenReturn(actor);
    when(actorMapper.toTmdbPersonResponse(actor)).thenReturn(sampleTmdbResponse());

    // When & Then
    mockMvc
        .perform(get("/person/819"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(819))
        .andExpect(jsonPath("$.name").value("Edward Norton"))
        .andExpect(jsonPath("$.birthday").value("1969-08-18"))
        .andExpect(jsonPath("$.place_of_birth").value("Boston, Massachusetts, USA"));
  }

  /**
   * Verifies that {@code GET /person/{id}} with a non-numeric identifier is rejected locally with
   * TMDB's 404 status code 34 error JSON rather than hitting upstream or throwing 500.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GET /person/{id}: rejects non-numeric id with TMDB 404 JSON")
  void personDetailsRejectsNonNumericId() throws Exception {
    // When & Then
    mockMvc
        .perform(get("/person/invalid-id"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.status_code").value(34))
        .andExpect(
            jsonPath("$.status_message").value("The resource you requested could not be found."));
  }

  /**
   * Verifies that {@code GET /person/{id}/movie_credits} returns 200 OK with TMDB cast credits.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GET /person/{id}/movie_credits: returns TMDB credits shape")
  void movieCreditsReturnsCreditsShape() throws Exception {
    // Given
    TmdbCastCredit credit =
        new TmdbCastCredit(550L, "Fight Club", "The Narrator", "1999-10-15", "/p.jpg", 8.4);
    TmdbPersonMovieCreditsResponse creditsResponse =
        new TmdbPersonMovieCreditsResponse(819L, List.of(credit));
    when(actorService.getFilmographyRaw(819L)).thenReturn(creditsResponse);

    // When & Then
    mockMvc
        .perform(get("/person/819/movie_credits"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(819))
        .andExpect(jsonPath("$.cast[0].id").value(550))
        .andExpect(jsonPath("$.cast[0].title").value("Fight Club"));
  }

  /**
   * Verifies that {@code GET /person/{id}/movie_credits} rejects non-numeric IDs with TMDB 404.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GET /person/{id}/movie_credits: rejects non-numeric id with 404")
  void movieCreditsRejectsNonNumericId() throws Exception {
    // When & Then
    mockMvc
        .perform(get("/person/abc/movie_credits"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status_code").value(34));
  }

  /**
   * Verifies that {@code GET /person/{id}/images} returns 200 OK with TMDB-compatible profile
   * images.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GET /person/{id}/images: returns TMDB images shape")
  void personImagesReturnsImagesShape() throws Exception {
    // Given
    ActorProfileImage entityImg =
        ActorProfileImage.builder()
            .filePath("/img.jpg")
            .aspectRatio(0.667)
            .height(1500)
            .width(1000)
            .iso6391("en")
            .voteAverage(6.5)
            .voteCount(14)
            .build();
    TmdbProfileImage facadeImg = new TmdbProfileImage("/img.jpg", 0.667, 1500, 1000, "en", 6.5, 14);

    when(actorService.getOrFetchImages(819L)).thenReturn(List.of(entityImg));
    when(actorMapper.toTmdbProfileImages(any())).thenReturn(List.of(facadeImg));

    // When & Then
    mockMvc
        .perform(get("/person/819/images"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(819))
        .andExpect(jsonPath("$.profiles[0].file_path").value("/img.jpg"))
        .andExpect(jsonPath("$.profiles[0].vote_average").value(6.5));
  }

  /**
   * Verifies that {@code GET /person/{id}/images} rejects non-numeric IDs with TMDB 404.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GET /person/{id}/images: rejects non-numeric id with 404")
  void personImagesRejectsNonNumericId() throws Exception {
    // When & Then
    mockMvc
        .perform(get("/person/xyz/images"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status_code").value(34));
  }

  /**
   * Verifies that {@code GET /person/popular} returns 200 OK with TMDB's popular person ranking
   * envelope.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GET /person/popular: returns TMDB popular persons list")
  void popularPersonsReturnsList() throws Exception {
    // Given
    TmdbPersonSummary summary =
        new TmdbPersonSummary(976L, "Jason Statham", "/j.jpg", 183.4, "Acting", 2, false);
    TmdbPersonSearchResponse searchResponse =
        new TmdbPersonSearchResponse(1, 500, 10000L, List.of(summary));
    when(actorService.getPopularRaw(1)).thenReturn(searchResponse);

    // When & Then
    mockMvc
        .perform(get("/person/popular").param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.total_pages").value(500))
        .andExpect(jsonPath("$.results[0].name").value("Jason Statham"));
  }

  /**
   * Verifies that {@code GET /search/person} returns 200 OK with TMDB search results.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("GET /search/person: returns TMDB person search list")
  void searchPersonsReturnsList() throws Exception {
    // Given
    TmdbPersonSummary summary =
        new TmdbPersonSummary(1245L, "Léa Seydoux", "/l.jpg", 12.3, "Acting", 1, false);
    TmdbPersonSearchResponse searchResponse =
        new TmdbPersonSearchResponse(1, 1, 1L, List.of(summary));
    when(actorService.searchRaw("seydoux", 1)).thenReturn(searchResponse);

    // When & Then
    mockMvc
        .perform(get("/search/person").param("query", "seydoux").param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_results").value(1))
        .andExpect(jsonPath("$.results[0].name").value("Léa Seydoux"));
  }

  /**
   * Verifies that {@link PersonFacadeController} forwards upstream {@link
   * RestClientResponseException} HTTP status code and JSON body verbatim so TMDB error responses
   * pass through unaltered.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("ExceptionHandler: forwards upstream RestClientResponseException verbatim")
  void handlesUpstreamError() throws Exception {
    // Given
    String tmdbErrorJson =
        "{\"status_code\":34,\"status_message\":\"The resource you requested could not be found.\"}";
    when(actorService.getOrFetchActorEntity(999999L))
        .thenThrow(
            HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                HttpHeaders.EMPTY,
                tmdbErrorJson.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

    // When & Then
    mockMvc
        .perform(get("/person/999999"))
        .andExpect(status().isNotFound())
        .andExpect(content().json(tmdbErrorJson));
  }

  /**
   * Verifies that {@link PersonFacadeController} maps network failures ({@link
   * ResourceAccessException}) to HTTP 502 Bad Gateway formatted as a TMDB error envelope.
   *
   * @throws Exception if mock MVC execution fails
   */
  @Test
  @DisplayName("ExceptionHandler: maps ResourceAccessException to 502 with TMDB error envelope")
  void handlesResourceAccessException() throws Exception {
    // Given
    when(actorService.getOrFetchActorEntity(819L))
        .thenThrow(new ResourceAccessException("Connection timed out"));

    // When & Then
    mockMvc
        .perform(get("/person/819"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.status_code").value(502))
        .andExpect(jsonPath("$.status_message").value("Upstream TMDB API is unreachable."));
  }
}
