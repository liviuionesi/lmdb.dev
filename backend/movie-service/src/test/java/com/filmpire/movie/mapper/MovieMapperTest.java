package com.filmpire.movie.mapper;

import com.filmpire.movie.dto.*;
import com.filmpire.movie.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MovieMapper.
 * Tests MapStruct mappings between entities and DTOs.
 */
@SpringBootTest(classes = MovieMapperTest.TestConfig.class)
@DisplayName("MovieMapper Tests")
class MovieMapperTest {

    @TestConfiguration
    @ComponentScan(basePackages = "com.filmpire.movie.mapper")
    static class TestConfig {
    }

    @Autowired
    private MovieMapper mapper;

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
        assertThat(dto.getId()).isEqualTo("mongo123");
        assertThat(dto.getTmdbId()).isEqualTo(550L);
        assertThat(dto.getTitle()).isEqualTo("Fight Club");
        assertThat(dto.getOverview()).isEqualTo("An insomniac office worker...");
        assertThat(dto.getPosterPath()).isEqualTo("/poster.jpg");
        assertThat(dto.getBackdropPath()).isEqualTo("/backdrop.jpg");
        assertThat(dto.getReleaseDate()).isEqualTo(LocalDate.of(1999, 10, 15));
        assertThat(dto.getVoteAverage()).isEqualTo(8.4);
        assertThat(dto.getVoteCount()).isEqualTo(25000);
        assertThat(dto.getGenres()).hasSize(2);
        assertThat(dto.getRuntime()).isEqualTo(139);
        assertThat(dto.getStatus()).isEqualTo("Released");
        assertThat(dto.getBudget()).isEqualTo(63000000L);
        assertThat(dto.getRevenue()).isEqualTo(100853753L);
        assertThat(dto.getSpokenLanguages()).containsExactly("English");
        assertThat(dto.getOriginalLanguage()).isEqualTo("en");
        assertThat(dto.getPopularity()).isEqualTo(450.5);
        assertThat(dto.getAdult()).isFalse();
        assertThat(dto.getImdbId()).isEqualTo("tt0137523");
        assertThat(dto.getTagline()).isEqualTo("Mischief. Mayhem. Soap.");
        assertThat(dto.getHomepage()).isEqualTo("http://www.foxmovies.com/movies/fight-club");
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
        assertThat(dto.getTmdbId()).isEqualTo(550L);
        assertThat(dto.getTitle()).isEqualTo("Fight Club");
        assertThat(dto.getOverview()).isEqualTo("An insomniac office worker...");
        assertThat(dto.getPosterPath()).isEqualTo("/poster.jpg");
        assertThat(dto.getBackdropPath()).isEqualTo("/backdrop.jpg");
        assertThat(dto.getReleaseDate()).isEqualTo(LocalDate.of(1999, 10, 15));
        assertThat(dto.getVoteAverage()).isEqualTo(8.4);
        assertThat(dto.getVoteCount()).isEqualTo(25000);
        assertThat(dto.getPopularity()).isEqualTo(450.5);
        assertThat(dto.getAdult()).isFalse();
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
        assertThat(dto.getId()).isEqualTo(28L);
        assertThat(dto.getName()).isEqualTo("Action");
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
        assertThat(dtos.get(0).getId()).isEqualTo(28L);
        assertThat(dtos.get(0).getName()).isEqualTo("Action");
        assertThat(dtos.get(1).getId()).isEqualTo(18L);
        assertThat(dtos.get(1).getName()).isEqualTo("Drama");
        assertThat(dtos.get(2).getId()).isEqualTo(53L);
        assertThat(dtos.get(2).getName()).isEqualTo("Thriller");
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
        assertThat(dto.getId()).isEqualTo("video123");
        assertThat(dto.getKey()).isEqualTo("dQw4w9WgXcQ");
        assertThat(dto.getName()).isEqualTo("Official Trailer");
        assertThat(dto.getSite()).isEqualTo("YouTube");
        assertThat(dto.getSize()).isEqualTo(1080);
        assertThat(dto.getType()).isEqualTo("Trailer");
        assertThat(dto.getOfficial()).isTrue();
        assertThat(dto.getPublishedAt()).isEqualTo("2020-01-01T00:00:00Z");
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
        assertThat(dto.getId()).isEqualTo(287L);
        assertThat(dto.getName()).isEqualTo("Brad Pitt");
        assertThat(dto.getCharacter()).isEqualTo("Tyler Durden");
        assertThat(dto.getProfilePath()).isEqualTo("/brad.jpg");
        assertThat(dto.getOrder()).isZero();
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
        assertThat(dto.getId()).isEqualTo(7467L);
        assertThat(dto.getName()).isEqualTo("David Fincher");
        assertThat(dto.getJob()).isEqualTo("Director");
        assertThat(dto.getDepartment()).isEqualTo("Directing");
        assertThat(dto.getProfilePath()).isEqualTo("/fincher.jpg");
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
        assertThat(dto.getTmdbId()).isEqualTo(550L);
        assertThat(dto.getTitle()).isEqualTo("Fight Club");
        assertThat(dto.getOverview()).isNull();
        assertThat(dto.getPosterPath()).isNull();
        assertThat(dto.getGenres()).isNull();
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
        assertThat(dtos.get(0).getTmdbId()).isEqualTo(550L);
        assertThat(dtos.get(0).getTitle()).isEqualTo("Fight Club");
        assertThat(dtos.get(1).getTmdbId()).isEqualTo(13L);
        assertThat(dtos.get(1).getTitle()).isEqualTo("Forrest Gump");
    }
}
