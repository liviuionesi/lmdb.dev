package com.filmpire.actor.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.filmpire.actor.client.dto.TmdbPersonResponse;
import com.filmpire.actor.client.dto.TmdbPersonSearchResponse.TmdbPersonSummary;
import com.filmpire.actor.dto.ActorDtos.ActorDto;
import com.filmpire.actor.dto.ActorDtos.ActorSummaryDto;
import com.filmpire.actor.model.Actor;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/**
 * Unit tests for {@link ActorMapper}, the MapStruct mapper component.
 *
 * <p>Exercises MapStruct's generated implementation directly via {@link Mappers#getMapper(Class)}
 * to verify field-by-field mapping fidelity across all conversions (Actor entity ↔ ActorDto ↔
 * TmdbPersonResponse ↔ ActorSummaryDto) and ensure null safety for sparse inputs.
 */
@DisplayName("ActorMapper Unit Tests")
class ActorMapperTest {

  private final ActorMapper mapper = Mappers.getMapper(ActorMapper.class);

  /**
   * Verifies that mapping from a domain {@link Actor} entity to a native {@link ActorDto}
   * accurately copies all profile attributes.
   */
  @Test
  @DisplayName("toDto: maps Actor entity to ActorDto")
  void toDtoMapsEntityToDto() {
    Actor entity =
        Actor.builder()
            .tmdbId(100L)
            .name("Test Actor")
            .biography("Bio text")
            .birthDate(LocalDate.of(1980, Month.MAY, 20))
            .birthPlace("London")
            .profilePath("/test.jpg")
            .popularity(85.5)
            .alsoKnownAs(List.of("Alias 1"))
            .knownForDepartment("Acting")
            .gender(1)
            .imdbId("nm1234567")
            .homepage("https://actor.org")
            .adult(false)
            .build();

    ActorDto dto = mapper.toDto(entity);

    assertThat(dto).isNotNull();
    assertThat(dto.tmdbId()).isEqualTo(100L);
    assertThat(dto.name()).isEqualTo("Test Actor");
    assertThat(dto.biography()).isEqualTo("Bio text");
    assertThat(dto.birthDate()).isEqualTo(LocalDate.of(1980, Month.MAY, 20));
    assertThat(dto.birthPlace()).isEqualTo("London");
    assertThat(dto.profilePath()).isEqualTo("/test.jpg");
    assertThat(dto.popularity()).isEqualTo(85.5);
    assertThat(dto.alsoKnownAs()).containsExactly("Alias 1");
    assertThat(dto.knownForDepartment()).isEqualTo("Acting");
    assertThat(dto.gender()).isEqualTo(1);
    assertThat(dto.imdbId()).isEqualTo("nm1234567");
    assertThat(dto.homepage()).isEqualTo("https://actor.org");
    assertThat(dto.adult()).isFalse();
  }

  /**
   * Verifies that passing a null {@link Actor} entity to {@link ActorMapper#toDto} returns null.
   */
  @Test
  @DisplayName("toDto: handles null input")
  void toDtoHandlesNull() {
    assertThat(mapper.toDto(null)).isNull();
  }

  /**
   * Verifies that mapping from a TMDB API {@link TmdbPersonResponse} record to a domain {@link
   * Actor} entity maps all matching attributes and initializes sync timestamp.
   */
  @Test
  @DisplayName("toEntity: maps TmdbPersonResponse to Actor entity")
  void toEntityMapsResponseToEntity() {
    TmdbPersonResponse response =
        new TmdbPersonResponse(
            200L,
            "Jane Doe",
            "Biography",
            LocalDate.of(1990, Month.JANUARY, 1),
            "Paris",
            "/jane.jpg",
            99.0,
            List.of("Janey"),
            "Directing",
            1,
            "nm7654321",
            "https://jane.com",
            false);

    Actor entity = mapper.toEntity(response);

    assertThat(entity).isNotNull();
    assertThat(entity.getTmdbId()).isEqualTo(200L);
    assertThat(entity.getName()).isEqualTo("Jane Doe");
    assertThat(entity.getBiography()).isEqualTo("Biography");
    assertThat(entity.getBirthDate()).isEqualTo(LocalDate.of(1990, Month.JANUARY, 1));
    assertThat(entity.getBirthPlace()).isEqualTo("Paris");
    assertThat(entity.getProfilePath()).isEqualTo("/jane.jpg");
    assertThat(entity.getPopularity()).isEqualTo(99.0);
    assertThat(entity.getAlsoKnownAs()).containsExactly("Janey");
    assertThat(entity.getKnownForDepartment()).isEqualTo("Directing");
    assertThat(entity.getGender()).isEqualTo(1);
    assertThat(entity.getImdbId()).isEqualTo("nm7654321");
    assertThat(entity.getHomepage()).isEqualTo("https://jane.com");
    assertThat(entity.getAdult()).isFalse();
    assertThat(entity.getSyncedAt()).isNotNull();
  }

  /**
   * Verifies that a null biography in a {@link TmdbPersonResponse} is normalized to an empty string
   * on the entity.
   */
  @Test
  @DisplayName("toEntity: handles null biography")
  void toEntityNormalizesNullBiography() {
    TmdbPersonResponse response =
        new TmdbPersonResponse(
            200L,
            "Jane Doe",
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null);

    Actor entity = mapper.toEntity(response);

    assertThat(entity).isNotNull();
    assertThat(entity.getBiography()).isEmpty();
  }

  /**
   * Verifies that passing a null {@link TmdbPersonResponse} to {@link ActorMapper#toEntity} returns
   * null.
   */
  @Test
  @DisplayName("toEntity: handles null input")
  void toEntityHandlesNull() {
    assertThat(mapper.toEntity(null)).isNull();
  }

  /**
   * Verifies that mapping from a domain {@link Actor} entity to a TMDB facade {@link
   * TmdbPersonResponse} accurately maps all fields to TMDB's response envelope shape.
   */
  @Test
  @DisplayName("toTmdbPersonResponse: maps Actor entity to TmdbPersonResponse")
  void toTmdbPersonResponseMapsEntityToResponse() {
    Actor entity =
        Actor.builder()
            .tmdbId(300L)
            .name("John Smith")
            .biography("Bio")
            .birthDate(LocalDate.of(1975, Month.OCTOBER, 15))
            .birthPlace("New York")
            .profilePath("/john.jpg")
            .popularity(70.0)
            .alsoKnownAs(List.of("Johnny"))
            .knownForDepartment("Acting")
            .gender(2)
            .imdbId("nm0000001")
            .homepage("https://john.org")
            .adult(false)
            .build();

    TmdbPersonResponse response = mapper.toTmdbPersonResponse(entity);

    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo(300L);
    assertThat(response.name()).isEqualTo("John Smith");
    assertThat(response.biography()).isEqualTo("Bio");
    assertThat(response.birthday()).isEqualTo(LocalDate.of(1975, Month.OCTOBER, 15));
    assertThat(response.placeOfBirth()).isEqualTo("New York");
    assertThat(response.profilePath()).isEqualTo("/john.jpg");
    assertThat(response.popularity()).isEqualTo(70.0);
    assertThat(response.alsoKnownAs()).containsExactly("Johnny");
    assertThat(response.knownForDepartment()).isEqualTo("Acting");
    assertThat(response.gender()).isEqualTo(2);
    assertThat(response.imdbId()).isEqualTo("nm0000001");
    assertThat(response.homepage()).isEqualTo("https://john.org");
    assertThat(response.adult()).isFalse();
  }

  /**
   * Verifies that passing a null {@link Actor} entity to {@link ActorMapper#toTmdbPersonResponse}
   * returns null.
   */
  @Test
  @DisplayName("toTmdbPersonResponse: handles null input")
  void toTmdbPersonResponseHandlesNull() {
    assertThat(mapper.toTmdbPersonResponse(null)).isNull();
  }

  /**
   * Verifies that mapping from a {@link TmdbPersonSummary} list hit to a native {@link
   * ActorSummaryDto} carries all summary fields.
   */
  @Test
  @DisplayName("toSummaryDto: maps TmdbPersonSummary to ActorSummaryDto")
  void toSummaryDtoMapsSummaryToDto() {
    TmdbPersonSummary summary =
        new TmdbPersonSummary(400L, "Summary Actor", "/sum.jpg", 45.0, "Acting", 2, false);

    ActorSummaryDto dto = mapper.toSummaryDto(summary);

    assertThat(dto).isNotNull();
    assertThat(dto.tmdbId()).isEqualTo(400L);
    assertThat(dto.name()).isEqualTo("Summary Actor");
    assertThat(dto.profilePath()).isEqualTo("/sum.jpg");
    assertThat(dto.popularity()).isEqualTo(45.0);
  }

  /**
   * Verifies that passing a null {@link TmdbPersonSummary} to {@link ActorMapper#toSummaryDto}
   * returns null.
   */
  @Test
  @DisplayName("toSummaryDto: handles null input")
  void toSummaryDtoHandlesNull() {
    assertThat(mapper.toSummaryDto(null)).isNull();
  }
}
