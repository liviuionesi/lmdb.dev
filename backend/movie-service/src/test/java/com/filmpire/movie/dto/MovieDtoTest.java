package com.filmpire.movie.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link MovieDto} record — the immutable response type of
 * the native {@code /api/v1} movie API.
 *
 * <p>As a record, DTO gets value equality, a builder (Lombok), and accessors
 * for free; these tests pin the behaviors clients depend on: construction,
 * value equality/hashCode (safe caching and comparison), Java serialization
 * round-trip (the DTO is cached), null-tolerance for sparse TMDB data, and a
 * useful toString for logs.</p>
 */
@DisplayName("MovieDto Tests")
class MovieDtoTest {

    /**
     * The builder must populate every accessor — the DTO is assembled field by
     * field from the entity/mapper, so a dropped field would silently vanish
     * from API responses.
     */
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
        assertThat(dto.id()).isEqualTo("mongo123");
        assertThat(dto.tmdbId()).isEqualTo(550L);
        assertThat(dto.title()).isEqualTo("Fight Club");
        assertThat(dto.voteAverage()).isEqualTo(8.4);
    }

    /**
     * The DTO must survive a Java-serialization round trip unchanged, because
     * it is stored in the (Redis) cache; a non-serializable field or broken
     * serialVersionUID would surface as cache write/read failures at runtime.
     *
     * @throws IOException            if the stream operations fail
     * @throws ClassNotFoundException if deserialization can't resolve the type
     */
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
        assertThat(deserialized.id()).isEqualTo(original.id());
        assertThat(deserialized.tmdbId()).isEqualTo(original.tmdbId());
        assertThat(deserialized.title()).isEqualTo(original.title());
        assertThat(deserialized.voteAverage()).isEqualTo(original.voteAverage());
    }

    /**
     * Record value-equality must hold field-by-field (equal for same content,
     * unequal otherwise, consistent hashCode) — relied on by cache dedup and by
     * any comparison of API results.
     */
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
        assertThat(dto1)
                .isEqualTo(dto2)
                .isNotEqualTo(dto3)
                .hasSameHashCodeAs(dto2);
    }

    /**
     * The nested {@link GenreDto} list must be carried through and remain
     * order-addressable, since the API exposes genres exactly as stored and the
     * UI renders them in that order.
     */
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
        assertThat(dto.genres()).hasSize(2);
        assertThat(dto.genres().get(0).id()).isEqualTo(28L);
        assertThat(dto.genres().get(0).name()).isEqualTo("Action");
    }

    /**
     * A DTO built from partial data must leave unset fields null rather than
     * failing — TMDB omits fields, and the DTO has to represent that faithfully
     * instead of substituting defaults that would misinform clients.
     */
    @Test
    @DisplayName("Should handle null values gracefully")
    void dto_WithNullValues_ShouldWork() {
        // Arrange & Act
        MovieDto dto = MovieDto.builder()
                .tmdbId(550L)
                .title("Fight Club")
                .build();

        // Assert
        assertThat(dto.tmdbId()).isEqualTo(550L);
        assertThat(dto.title()).isEqualTo("Fight Club");
        assertThat(dto.overview()).isNull();
        assertThat(dto.genres()).isNull();
        assertThat(dto.posterPath()).isNull();
    }

    /**
     * The record's toString must include the key identifying fields (id, title,
     * rating) so log lines and debugging output are actually useful rather than
     * an opaque object reference.
     */
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
        assertThat(toString).contains("550", "Fight Club", "8.4");
    }
}

