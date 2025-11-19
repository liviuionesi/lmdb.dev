package com.filmpire.movie.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MovieDto.
 * Tests DTO serialization, deserialization, and data integrity.
 */
@DisplayName("MovieDto Tests")
class MovieDtoTest {

    @Test
    @DisplayName("Should create DTO with builder")
    void builder_ShouldCreateDto() {
        // Arrange & Act
        MovieDto dto = MovieDto.builder()
                .id("mongo123")
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
                .build();

        // Assert
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("mongo123");
        assertThat(dto.getTmdbId()).isEqualTo(550L);
        assertThat(dto.getTitle()).isEqualTo("Fight Club");
        assertThat(dto.getVoteAverage()).isEqualTo(8.4);
    }

    @Test
    @DisplayName("Should be serializable")
    void dto_ShouldBeSerializable() throws IOException, ClassNotFoundException {
        // Arrange
        MovieDto original = MovieDto.builder()
                .id("mongo123")
                .tmdbId(550L)
                .title("Fight Club")
                .voteAverage(8.4)
                .runtime(139)
                .build();

        // Act - Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.close();

        // Act - Deserialize
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        MovieDto deserialized = (MovieDto) ois.readObject();
        ois.close();

        // Assert
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getId()).isEqualTo(original.getId());
        assertThat(deserialized.getTmdbId()).isEqualTo(original.getTmdbId());
        assertThat(deserialized.getTitle()).isEqualTo(original.getTitle());
        assertThat(deserialized.getVoteAverage()).isEqualTo(original.getVoteAverage());
    }

    @Test
    @DisplayName("Should handle equals and hashCode correctly")
    void equalsAndHashCode_ShouldWork() {
        // Arrange
        MovieDto dto1 = MovieDto.builder()
                .id("mongo123")
                .tmdbId(550L)
                .title("Fight Club")
                .build();

        MovieDto dto2 = MovieDto.builder()
                .id("mongo123")
                .tmdbId(550L)
                .title("Fight Club")
                .build();

        MovieDto dto3 = MovieDto.builder()
                .id("mongo456")
                .tmdbId(13L)
                .title("Forrest Gump")
                .build();

        // Act & Assert
        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1).isNotEqualTo(dto3);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
    }

    @Test
    @DisplayName("Should handle genres correctly")
    void dto_WithGenres_ShouldWork() {
        // Arrange
        List<GenreDto> genres = Arrays.asList(
                GenreDto.builder().id(28L).name("Action").build(),
                GenreDto.builder().id(18L).name("Drama").build()
        );

        // Act
        MovieDto dto = MovieDto.builder()
                .tmdbId(550L)
                .title("Fight Club")
                .genres(genres)
                .build();

        // Assert
        assertThat(dto.getGenres()).hasSize(2);
        assertThat(dto.getGenres().get(0).getId()).isEqualTo(28L);
        assertThat(dto.getGenres().get(0).getName()).isEqualTo("Action");
    }

    @Test
    @DisplayName("Should handle null values gracefully")
    void dto_WithNullValues_ShouldWork() {
        // Arrange & Act
        MovieDto dto = MovieDto.builder()
                .tmdbId(550L)
                .title("Fight Club")
                .build();

        // Assert
        assertThat(dto.getTmdbId()).isEqualTo(550L);
        assertThat(dto.getTitle()).isEqualTo("Fight Club");
        assertThat(dto.getOverview()).isNull();
        assertThat(dto.getGenres()).isNull();
        assertThat(dto.getPosterPath()).isNull();
    }

    @Test
    @DisplayName("Should handle toString correctly")
    void toString_ShouldWork() {
        // Arrange
        MovieDto dto = MovieDto.builder()
                .tmdbId(550L)
                .title("Fight Club")
                .voteAverage(8.4)
                .build();

        // Act
        String toString = dto.toString();

        // Assert
        assertThat(toString).contains("550");
        assertThat(toString).contains("Fight Club");
        assertThat(toString).contains("8.4");
    }
}

