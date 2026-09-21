package dev.lmdb.movie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.lmdb.movie.client.TmdbClient;
import dev.lmdb.movie.client.dto.TmdbCollectionResponse;
import dev.lmdb.movie.client.dto.TmdbMovieListResponse;
import dev.lmdb.movie.dto.CollectionDto;
import dev.lmdb.movie.mapper.MovieMapper;
import dev.lmdb.movie.model.Movie;
import dev.lmdb.movie.repository.MovieRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("MovieService Collection Tests")
class MovieServiceCollectionTest {

  @Mock private MovieRepository movieRepository;
  @Mock private TmdbClient tmdbClient;
  @Mock private MovieMapper movieMapper;
  @Mock private ObjectProvider<MovieService> selfProvider;

  @InjectMocks private MovieService movieService;

  private final String tmdbApiKey = "test-api-key";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(movieService, "tmdbApiKey", tmdbApiKey);
    lenient().when(selfProvider.getObject()).thenReturn(movieService);
  }

  @Test
  @DisplayName("getCollection - Should fetch from TMDB and save each movie")
  void getCollection_ShouldFetchFromTMDBAndSaveEachMovie() {
    // Arrange
    Long collectionId = 87L;
    TmdbMovieListResponse.TmdbMovieItem item =
        new TmdbMovieListResponse.TmdbMovieItem(
            62L,
            "2001: A Space Odyssey",
            "Humanity finds...",
            "/poster.jpg",
            "/backdrop.jpg",
            "1968-04-09",
            8.3,
            10000,
            List.of(878L),
            50.0,
            false,
            "en");
    TmdbCollectionResponse tmdbResponse =
        new TmdbCollectionResponse(collectionId, "Space Odyssey Collection", List.of(item));

    when(tmdbClient.getCollection(collectionId, tmdbApiKey)).thenReturn(tmdbResponse);
    when(movieRepository.findByTmdbId(62L)).thenReturn(Optional.empty());
    when(movieRepository.save(any(Movie.class))).thenAnswer(i -> i.getArgument(0));

    // Act
    CollectionDto result = movieService.getCollection(collectionId);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(collectionId);
    assertThat(result.name()).isEqualTo("Space Odyssey Collection");
    assertThat(result.movies()).hasSize(1);
    assertThat(result.movies().get(0).tmdbId()).isEqualTo(62L);
    assertThat(result.movies().get(0).title()).isEqualTo("2001: A Space Odyssey");

    verify(tmdbClient).getCollection(collectionId, tmdbApiKey);
    verify(movieRepository).save(argThat(movie -> movie.getTmdbId().equals(62L)));
  }
}
