package com.filmpire.movie.mapper;

import com.filmpire.movie.dto.*;
import com.filmpire.movie.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MovieMapper.
 * Tests MapStruct mappings between entities and DTOs using the generated implementation.
 */
@DisplayName("MovieMapper Tests")
class MovieMapperTest {

    // Use MapStruct's factory to get the generated implementation directly
    private final MovieMapper mapper = org.mapstruct.factory.Mappers.getMapper(MovieMapper.class);

    @Test
    @DisplayName("Should map Movie to MovieDto correctly")
    void movieToDto_ShouldMapCorrectly() {
        // Arrange
        Movie movie = Movie.builder()
                .id("mongo123")
                .tmdbId(550L)
                .title("Fight Club")
                .overview("An insomniac office worker...")
                .posterPath("/poster.jpg")
                .backdropPath("/backdrop.jpg")
                .releaseDate(LocalDate.of(1999, 10, 15))
                .voteAverage(8.4)
                .voteCount(25000)
                .genres(Arrays.asList(
                        Genre.builder().id(18L).name("Drama").build(),
                        Genre.builder().id(53L).name("Thriller").build()
                ))
                .runtime(139)
                .status("Released")
                .budget(63000000L)
                .revenue(100853753L)
                .spokenLanguages(Arrays.asList("English"))
                .originalLanguage("en")
                .popularity(450.5)
                .adult(false)
                .imdbId("tt0137523")
                .tagline("Mischief. Mayhem. Soap.")
                .homepage("http://www.foxmovies.com/movies/fight-club")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .tmdbSyncVersion(1)
                .build();

        // Act
        MovieDto dto = mapper.toDto(movie);

        // Assert
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo("mongo123");
        assertThat(dto.tmdbId()).isEqualTo(550L);
        assertThat(dto.title()).isEqualTo("Fight Club");
        assertThat(dto.overview()).isEqualTo("An insomniac office worker...");
        assertThat(dto.posterPath()).isEqualTo("/poster.jpg");
        assertThat(dto.backdropPath()).isEqualTo("/backdrop.jpg");
        assertThat(dto.releaseDate()).isEqualTo(LocalDate.of(1999, 10, 15));
        assertThat(dto.voteAverage()).isEqualTo(8.4);
        assertThat(dto.voteCount()).isEqualTo(25000);
        assertThat(dto.genres()).hasSize(2);
        assertThat(dto.runtime()).isEqualTo(139);
        assertThat(dto.status()).isEqualTo("Released");
        assertThat(dto.budget()).isEqualTo(63000000L);
        assertThat(dto.revenue()).isEqualTo(100853753L);
        assertThat(dto.spokenLanguages()).containsExactly("English");
        assertThat(dto.originalLanguage()).isEqualTo("en");
        assertThat(dto.popularity()).isEqualTo(450.5);
        assertThat(dto.adult()).isFalse();
        assertThat(dto.imdbId()).isEqualTo("tt0137523");
        assertThat(dto.tagline()).isEqualTo("Mischief. Mayhem. Soap.");
        assertThat(dto.homepage()).isEqualTo("http://www.foxmovies.com/movies/fight-club");
    }

    @Test
    @DisplayName("Should map Movie to MovieListDto correctly")
    void movieToListDto_ShouldMapCorrectly() {
        // Arrange
        Movie movie = Movie.builder()
                .id("mongo123")
                .tmdbId(550L)
                .title("Fight Club")
                .overview("An insomniac office worker...")
                .posterPath("/poster.jpg")
                .backdropPath("/backdrop.jpg")
                .releaseDate(LocalDate.of(1999, 10, 15))
                .voteAverage(8.4)
                .voteCount(25000)
                .popularity(450.5)
                .adult(false)
                .build();

        // Act
        MovieListDto dto = mapper.toListDto(movie);

        // Assert
        assertThat(dto).isNotNull();
        assertThat(dto.tmdbId()).isEqualTo(550L);
        assertThat(dto.title()).isEqualTo("Fight Club");
        assertThat(dto.overview()).isEqualTo("An insomniac office worker...");
        assertThat(dto.posterPath()).isEqualTo("/poster.jpg");
        assertThat(dto.backdropPath()).isEqualTo("/backdrop.jpg");
        assertThat(dto.releaseDate()).isEqualTo(LocalDate.of(1999, 10, 15));
        assertThat(dto.voteAverage()).isEqualTo(8.4);
        assertThat(dto.voteCount()).isEqualTo(25000);
        assertThat(dto.popularity()).isEqualTo(450.5);
        assertThat(dto.adult()).isFalse();
    }

    @Test
    @DisplayName("Should map Genre to GenreDto correctly")
    void genreToDto_ShouldMapCorrectly() {
        // Arrange
        Genre genre = Genre.builder()
                .id(28L)
                .name("Action")
                .build();

        // Act
        GenreDto dto = mapper.toDto(genre);

        // Assert
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(28L);
        assertThat(dto.name()).isEqualTo("Action");
    }

    @Test
    @DisplayName("Should map list of Genres to list of GenreDtos")
    void genresListToDto_ShouldMapCorrectly() {
        // Arrange
        List<Genre> genres = Arrays.asList(
                Genre.builder().id(28L).name("Action").build(),
                Genre.builder().id(18L).name("Drama").build(),
                Genre.builder().id(53L).name("Thriller").build()
        );

        // Act
        List<GenreDto> dtos = mapper.genresToDto(genres);

        // Assert
        assertThat(dtos).hasSize(3);
        assertThat(dtos.get(0).id()).isEqualTo(28L);
        assertThat(dtos.get(0).name()).isEqualTo("Action");
        assertThat(dtos.get(1).id()).isEqualTo(18L);
        assertThat(dtos.get(1).name()).isEqualTo("Drama");
        assertThat(dtos.get(2).id()).isEqualTo(53L);
        assertThat(dtos.get(2).name()).isEqualTo("Thriller");
    }

    @Test
    @DisplayName("Should map Video to VideoDto correctly")
    void videoToDto_ShouldMapCorrectly() {
        // Arrange
        Video video = Video.builder()
                .id("video123")
                .key("dQw4w9WgXcQ")
                .name("Official Trailer")
                .site("YouTube")
                .size(1080)
                .type("Trailer")
                .official(true)
                .publishedAt("2020-01-01T00:00:00Z")
                .build();

        // Act
        VideoDto dto = mapper.toDto(video);

        // Assert
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo("video123");
        assertThat(dto.key()).isEqualTo("dQw4w9WgXcQ");
        assertThat(dto.name()).isEqualTo("Official Trailer");
        assertThat(dto.site()).isEqualTo("YouTube");
        assertThat(dto.size()).isEqualTo(1080);
        assertThat(dto.type()).isEqualTo("Trailer");
        assertThat(dto.official()).isTrue();
        assertThat(dto.publishedAt()).isEqualTo("2020-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("Should map Cast to CastDto correctly")
    void castToDto_ShouldMapCorrectly() {
        // Arrange
        Cast cast = Cast.builder()
                .id(287L)
                .name("Brad Pitt")
                .character("Tyler Durden")
                .profilePath("/brad.jpg")
                .order(0)
                .build();

        // Act
        CastDto dto = mapper.toDto(cast);

        // Assert
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(287L);
        assertThat(dto.name()).isEqualTo("Brad Pitt");
        assertThat(dto.character()).isEqualTo("Tyler Durden");
        assertThat(dto.profilePath()).isEqualTo("/brad.jpg");
        assertThat(dto.order()).isZero();
    }

    @Test
    @DisplayName("Should map Crew to CrewDto correctly")
    void crewToDto_ShouldMapCorrectly() {
        // Arrange
        Crew crew = Crew.builder()
                .id(7467L)
                .name("David Fincher")
                .job("Director")
                .department("Directing")
                .profilePath("/fincher.jpg")
                .build();

        // Act
        CrewDto dto = mapper.toDto(crew);

        // Assert
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(7467L);
        assertThat(dto.name()).isEqualTo("David Fincher");
        assertThat(dto.job()).isEqualTo("Director");
        assertThat(dto.department()).isEqualTo("Directing");
        assertThat(dto.profilePath()).isEqualTo("/fincher.jpg");
    }

    @Test
    @DisplayName("Should handle null values gracefully")
    void mapping_WithNullValues_ShouldHandleGracefully() {
        // Arrange
        Movie movie = Movie.builder()
                .tmdbId(550L)
                .title("Fight Club")
                .build();

        // Act
        MovieDto dto = mapper.toDto(movie);

        // Assert
        assertThat(dto).isNotNull();
        assertThat(dto.tmdbId()).isEqualTo(550L);
        assertThat(dto.title()).isEqualTo("Fight Club");
        assertThat(dto.overview()).isNull();
        assertThat(dto.posterPath()).isNull();
        assertThat(dto.genres()).isNull();
    }

    @Test
    @DisplayName("Should map list of Movies to list of MovieListDtos")
    void moviesListToDto_ShouldMapCorrectly() {
        // Arrange
        List<Movie> movies = Arrays.asList(
                Movie.builder()
                        .tmdbId(550L)
                        .title("Fight Club")
                        .voteAverage(8.4)
                        .build(),
                Movie.builder()
                        .tmdbId(13L)
                        .title("Forrest Gump")
                        .voteAverage(8.8)
                        .build()
        );

        // Act
        List<MovieListDto> dtos = mapper.toListDto(movies);

        // Assert
        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).tmdbId()).isEqualTo(550L);
        assertThat(dtos.get(0).title()).isEqualTo("Fight Club");
        assertThat(dtos.get(1).tmdbId()).isEqualTo(13L);
        assertThat(dtos.get(1).title()).isEqualTo("Forrest Gump");
    }
}
