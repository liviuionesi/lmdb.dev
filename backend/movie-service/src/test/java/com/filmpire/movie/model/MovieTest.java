package com.filmpire.movie.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Movie entity.
 * Tests entity behavior, builders, and data integrity.
 */
@DisplayName("Movie Entity Tests")
class MovieTest {

    @Test
    @DisplayName("Should create movie with builder")
    void builder_ShouldCreateMovie() {
        // Arrange & Act
        Movie movie = Movie.builder()
                .tmdbId(550L)
                .title("Fight Club")
                .overview("An insomniac office worker...")
                .posterPath("/poster.jpg")
                .backdropPath("/backdrop.jpg")
                .releaseDate(LocalDate.of(1999, 10, 15))
                .voteAverage(8.4)
                .voteCount(25000)
                .runtime(139)
                .status("Released")
                .budget(63000000L)
                .revenue(100853753L)
                .popularity(450.5)
                .adult(false)
                .imdbId("tt0137523")
                .tagline("Mischief. Mayhem. Soap.")
                .homepage("http://www.foxmovies.com/movies/fight-club")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .tmdbSyncVersion(1)
                .build();

        // Assert
        assertThat(movie).isNotNull();
        assertThat(movie.getTmdbId()).isEqualTo(550L);
        assertThat(movie.getTitle()).isEqualTo("Fight Club");
        assertThat(movie.getVoteAverage()).isEqualTo(8.4);
        assertThat(movie.getRuntime()).isEqualTo(139);
        assertThat(movie.getAdult()).isFalse();
    }

    @Test
    @DisplayName("Should handle null values")
    void movie_WithNullValues_ShouldWork() {
        // Arrange & Act
        Movie movie = Movie.builder()
                .tmdbId(550L)
                .title("Fight Club")
                .build();

        // Assert
        assertThat(movie.getTmdbId()).isEqualTo(550L);
        assertThat(movie.getTitle()).isEqualTo("Fight Club");
        assertThat(movie.getOverview()).isNull();
        assertThat(movie.getGenres()).isNull();
        assertThat(movie.getRuntime()).isNull();
    }

    @Test
    @DisplayName("Should set and get all properties correctly")
    void settersAndGetters_ShouldWork() {
        // Arrange
        Movie movie = new Movie();
        LocalDate releaseDate = LocalDate.of(1999, 10, 15);
        LocalDateTime now = LocalDateTime.now();
        List<Genre> genres = Arrays.asList(
                Genre.builder().id(18L).name("Drama").build()
        );

        // Act
        movie.setId("mongo123");
        movie.setTmdbId(550L);
        movie.setTitle("Fight Club");
        movie.setOverview("An insomniac office worker...");
        movie.setPosterPath("/poster.jpg");
        movie.setBackdropPath("/backdrop.jpg");
        movie.setReleaseDate(releaseDate);
        movie.setVoteAverage(8.4);
        movie.setVoteCount(25000);
        movie.setGenres(genres);
        movie.setRuntime(139);
        movie.setStatus("Released");
        movie.setBudget(63000000L);
        movie.setRevenue(100853753L);
        movie.setPopularity(450.5);
        movie.setAdult(false);
        movie.setImdbId("tt0137523");
        movie.setCreatedAt(now);
        movie.setUpdatedAt(now);

        // Assert
        assertThat(movie.getId()).isEqualTo("mongo123");
        assertThat(movie.getTmdbId()).isEqualTo(550L);
        assertThat(movie.getTitle()).isEqualTo("Fight Club");
        assertThat(movie.getOverview()).isEqualTo("An insomniac office worker...");
        assertThat(movie.getReleaseDate()).isEqualTo(releaseDate);
        assertThat(movie.getGenres()).hasSize(1);
        assertThat(movie.getCreatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Should handle equals and hashCode correctly")
    void equalsAndHashCode_ShouldWork() {
        // Arrange
        Movie movie1 = Movie.builder()
                .id("mongo123")
                .tmdbId(550L)
                .title("Fight Club")
                .build();

        Movie movie2 = Movie.builder()
                .id("mongo123")
                .tmdbId(550L)
                .title("Fight Club")
                .build();

        Movie movie3 = Movie.builder()
                .id("mongo456")
                .tmdbId(13L)
                .title("Forrest Gump")
                .build();

        // Act & Assert
        assertThat(movie1).isEqualTo(movie2);
        assertThat(movie1).isNotEqualTo(movie3);
        assertThat(movie1.hashCode()).isEqualTo(movie2.hashCode());
    }

    @Test
    @DisplayName("Should create movie with genres")
    void movie_WithGenres_ShouldWork() {
        // Arrange
        List<Genre> genres = Arrays.asList(
                Genre.builder().id(28L).name("Action").build(),
                Genre.builder().id(18L).name("Drama").build(),
                Genre.builder().id(53L).name("Thriller").build()
        );

        // Act
        Movie movie = Movie.builder()
                .tmdbId(550L)
                .title("Fight Club")
                .genres(genres)
                .build();

        // Assert
        assertThat(movie.getGenres()).hasSize(3);
        assertThat(movie.getGenres().get(0).getId()).isEqualTo(28L);
        assertThat(movie.getGenres().get(0).getName()).isEqualTo("Action");
    }

    @Test
    @DisplayName("Should create movie with production companies")
    void movie_WithProductionCompanies_ShouldWork() {
        // Arrange
        List<ProductionCompany> companies = Arrays.asList(
                ProductionCompany.builder()
                        .id(1L)
                        .name("20th Century Fox")
                        .logoPath("/logo.png")
                        .originCountry("US")
                        .build()
        );

        // Act
        Movie movie = Movie.builder()
                .tmdbId(550L)
                .title("Fight Club")
                .productionCompanies(companies)
                .build();

        // Assert
        assertThat(movie.getProductionCompanies()).hasSize(1);
        assertThat(movie.getProductionCompanies().get(0).getName()).isEqualTo("20th Century Fox");
    }

    @Test
    @DisplayName("Should handle spoken languages")
    void movie_WithSpokenLanguages_ShouldWork() {
        // Arrange
        List<String> languages = Arrays.asList("English", "Spanish", "French");

        // Act
        Movie movie = Movie.builder()
                .tmdbId(550L)
                .title("Fight Club")
                .spokenLanguages(languages)
                .originalLanguage("en")
                .build();

        // Assert
        assertThat(movie.getSpokenLanguages()).hasSize(3);
        assertThat(movie.getSpokenLanguages()).containsExactly("English", "Spanish", "French");
        assertThat(movie.getOriginalLanguage()).isEqualTo("en");
    }

    @Test
    @DisplayName("Should handle timestamps correctly")
    void movie_WithTimestamps_ShouldWork() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        // Act
        Movie movie = Movie.builder()
                .tmdbId(550L)
                .title("Fight Club")
                .createdAt(now)
                .updatedAt(now)
                .tmdbSyncVersion(1)
                .build();

        // Assert
        assertThat(movie.getCreatedAt()).isEqualTo(now);
        assertThat(movie.getUpdatedAt()).isEqualTo(now);
        assertThat(movie.getTmdbSyncVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle large numeric values")
    void movie_WithLargeValues_ShouldWork() {
        // Arrange & Act
        Movie movie = Movie.builder()
                .tmdbId(550L)
                .title("Fight Club")
                .budget(1000000000L) // 1 billion
                .revenue(2500000000L) // 2.5 billion
                .voteCount(Integer.MAX_VALUE)
                .popularity(Double.MAX_VALUE)
                .build();

        // Assert
        assertThat(movie.getBudget()).isEqualTo(1000000000L);
        assertThat(movie.getRevenue()).isEqualTo(2500000000L);
        assertThat(movie.getVoteCount()).isEqualTo(Integer.MAX_VALUE);
        assertThat(movie.getPopularity()).isEqualTo(Double.MAX_VALUE);
    }

    @Test
    @DisplayName("Should handle edge case ratings")
    void movie_WithEdgeCaseRatings_ShouldWork() {
        // Arrange & Act
        Movie movie1 = Movie.builder()
                .tmdbId(1L)
                .title("Perfect Movie")
                .voteAverage(10.0)
                .build();

        Movie movie2 = Movie.builder()
                .tmdbId(2L)
                .title("Worst Movie")
                .voteAverage(0.0)
                .build();

        // Assert
        assertThat(movie1.getVoteAverage()).isEqualTo(10.0);
        assertThat(movie2.getVoteAverage()).isEqualTo(0.0);
    }
}

