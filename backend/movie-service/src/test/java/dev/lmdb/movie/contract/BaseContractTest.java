package dev.lmdb.movie.contract;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.lmdb.movie.controller.MovieController;
import dev.lmdb.movie.dto.MovieDto;
import dev.lmdb.movie.service.MovieService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base setup class for Spring Cloud Contract generated producer tests in {@code movie-service}.
 * Mocks {@link MovieService} responses and configures {@link RestAssuredMockMvc}.
 */
public abstract class BaseContractTest {

  /**
   * Sets up mock MVC controller environment with stubbed movie responses before each contract test
   * execution.
   */
  @BeforeEach
  public void setup() {
    MovieService movieService = mock(MovieService.class);

    MovieDto movieDto =
        MovieDto.builder()
            .id("550")
            .tmdbId(550L)
            .title("Fight Club")
            .overview(
                "A ticking-time-bomb insomniac and a slippery soap salesman channel primal male"
                    + " aggression into a shocking new form of therapy.")
            .posterPath("/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg")
            .backdropPath("/hZkgoQY85KGDiDSpMwWrhFSMi1r.jpg")
            .releaseDate(LocalDate.of(1999, Month.OCTOBER, 15))
            .voteAverage(8.43)
            .voteCount(26280)
            .build();

    when(movieService.getMovieById(550L)).thenReturn(movieDto);

    MovieController movieController = new MovieController(movieService);
    RestAssuredMockMvc.standaloneSetup(movieController);
  }
}
