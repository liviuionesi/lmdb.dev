package dev.lmdb.actor.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lmdb.actor.client.dto.TmdbPersonResponse;
import dev.lmdb.actor.client.dto.TmdbPersonSearchResponse.TmdbPersonSummary;
import dev.lmdb.actor.dto.ActorDtos.ActorDto;
import dev.lmdb.actor.dto.ActorDtos.ActorSummaryDto;
import dev.lmdb.actor.model.Actor;
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

  /**
   * Verifies that mapping a list of {@link TmdbPersonSummary} records to native summary DTOs
   * preserves all elements and handles null/empty lists.
   */
  @Test
  @DisplayName("toSummaryDtos: maps list of TmdbPersonSummary records")
  void toSummaryDtosMapsList() {
    TmdbPersonSummary summary1 =
        new TmdbPersonSummary(401L, "Actor One", "/one.jpg", 10.0, "Acting", 1, false);
    TmdbPersonSummary summary2 =
        new TmdbPersonSummary(402L, "Actor Two", "/two.jpg", 20.0, "Directing", 2, false);

    List<ActorSummaryDto> dtos = mapper.toSummaryDtos(List.of(summary1, summary2));

    assertThat(dtos).hasSize(2);
    assertThat(dtos.get(0).tmdbId()).isEqualTo(401L);
    assertThat(dtos.get(1).tmdbId()).isEqualTo(402L);
    assertThat(mapper.toSummaryDtos(null)).isNull();
  }

  /**
   * Verifies that mapping a {@link
   * dev.lmdb.actor.client.dto.TmdbPersonMovieCreditsResponse.TmdbCastCredit} to {@link
   * dev.lmdb.actor.dto.ActorDtos.FilmographyEntryDto} correctly maps id to movieId and defaults a
   * null releaseDate to an empty string.
   */
  @Test
  @DisplayName(
      "toFilmographyEntryDto: maps cast credit and defaults null release date to empty string")
  void toFilmographyEntryDtoMapsFields() {
    var creditWithDate =
        new dev.lmdb.actor.client.dto.TmdbPersonMovieCreditsResponse.TmdbCastCredit(
            550L, "Fight Club", "The Narrator", "1999-10-15", "/poster.jpg", 8.4);

    var dto = mapper.toFilmographyEntryDto(creditWithDate);

    assertThat(dto).isNotNull();
    assertThat(dto.movieId()).isEqualTo(550L);
    assertThat(dto.title()).isEqualTo("Fight Club");
    assertThat(dto.character()).isEqualTo("The Narrator");
    assertThat(dto.releaseDate()).isEqualTo("1999-10-15");
    assertThat(dto.posterPath()).isEqualTo("/poster.jpg");
    assertThat(dto.voteAverage()).isEqualTo(8.4);

    var creditWithoutDate =
        new dev.lmdb.actor.client.dto.TmdbPersonMovieCreditsResponse.TmdbCastCredit(
            551L, "Undated Movie", "Lead", null, "/undated.jpg", 6.0);
    var undatedDto = mapper.toFilmographyEntryDto(creditWithoutDate);
    assertThat(undatedDto.releaseDate()).isEmpty();
    assertThat(mapper.toFilmographyEntryDto(null)).isNull();
  }

  /** Verifies that mapping a list of cast credits maps all elements. */
  @Test
  @DisplayName("toFilmographyEntryDtos: maps list of cast credits")
  void toFilmographyEntryDtosMapsList() {
    var credit1 =
        new dev.lmdb.actor.client.dto.TmdbPersonMovieCreditsResponse.TmdbCastCredit(
            1L, "Movie 1", "Role 1", "2020-01-01", "/p1.jpg", 7.0);
    var credit2 =
        new dev.lmdb.actor.client.dto.TmdbPersonMovieCreditsResponse.TmdbCastCredit(
            2L, "Movie 2", "Role 2", "2021-01-01", "/p2.jpg", 8.0);

    var dtos = mapper.toFilmographyEntryDtos(List.of(credit1, credit2));

    assertThat(dtos).hasSize(2);
    assertThat(dtos.get(0).movieId()).isEqualTo(1L);
    assertThat(dtos.get(1).movieId()).isEqualTo(2L);
    assertThat(mapper.toFilmographyEntryDtos(null)).isNull();
  }

  /**
   * Verifies mapping from {@link dev.lmdb.actor.model.ActorProfileImage} to {@link
   * dev.lmdb.actor.dto.ActorDtos.ActorImageDto}.
   */
  @Test
  @DisplayName("toImageDto and toImageDtos: maps domain image entity to DTO")
  void toImageDtoMapsEntityToDto() {
    var entity =
        dev.lmdb.actor.model.ActorProfileImage.builder()
            .filePath("/profile.jpg")
            .aspectRatio(0.667)
            .height(2100)
            .width(1400)
            .iso6391("en")
            .voteAverage(5.5)
            .voteCount(10)
            .build();

    var dto = mapper.toImageDto(entity);

    assertThat(dto).isNotNull();
    assertThat(dto.filePath()).isEqualTo("/profile.jpg");
    assertThat(dto.aspectRatio()).isEqualTo(0.667);
    assertThat(dto.height()).isEqualTo(2100);
    assertThat(dto.width()).isEqualTo(1400);
    assertThat(dto.iso6391()).isEqualTo("en");
    assertThat(dto.voteAverage()).isEqualTo(5.5);
    assertThat(dto.voteCount()).isEqualTo(10);
    assertThat(mapper.toImageDto(null)).isNull();

    var list = mapper.toImageDtos(List.of(entity));
    assertThat(list).hasSize(1);
    assertThat(mapper.toImageDtos(null)).isNull();
  }

  /**
   * Verifies mapping from {@link
   * dev.lmdb.actor.client.dto.TmdbPersonImagesResponse.TmdbProfileImage} to domain entity.
   */
  @Test
  @DisplayName(
      "toProfileImageEntity and toProfileImageEntities: maps TMDB image record to domain entity")
  void toProfileImageEntityMapsRecordToEntity() {
    // Given
    var tmdbImage =
        new dev.lmdb.actor.client.dto.TmdbPersonImagesResponse.TmdbProfileImage(
            "/tmdb_img.jpg", 0.667, 1500, 1000, "en", 6.2, 12);

    // When
    var entity = mapper.toProfileImageEntity(tmdbImage);

    // Then
    assertThat(entity).isNotNull();
    assertThat(entity.getFilePath()).isEqualTo("/tmdb_img.jpg");
    assertThat(entity.getAspectRatio()).isEqualTo(0.667);
    assertThat(entity.getHeight()).isEqualTo(1500);
    assertThat(entity.getWidth()).isEqualTo(1000);
    assertThat(entity.getIso6391()).isEqualTo("en");
    assertThat(entity.getVoteAverage()).isEqualTo(6.2);
    assertThat(entity.getVoteCount()).isEqualTo(12);
    assertThat(mapper.toProfileImageEntity(null)).isNull();

    var entities = mapper.toProfileImageEntities(List.of(tmdbImage));
    assertThat(entities).hasSize(1);
    assertThat(mapper.toProfileImageEntities(null)).isNull();
  }

  /**
   * Verifies mapping from {@link dev.lmdb.actor.model.ActorProfileImage} to TMDB facade image
   * record.
   */
  @Test
  @DisplayName(
      "toTmdbProfileImage and toTmdbProfileImages: maps domain image entity to TMDB facade record")
  void toTmdbProfileImageMapsEntityToRecord() {
    // Given
    var entity =
        dev.lmdb.actor.model.ActorProfileImage.builder()
            .filePath("/facade_img.jpg")
            .aspectRatio(0.667)
            .height(1800)
            .width(1200)
            .iso6391(null)
            .voteAverage(7.1)
            .voteCount(25)
            .build();

    // When
    var imageRecord = mapper.toTmdbProfileImage(entity);

    // Then
    assertThat(imageRecord).isNotNull();
    assertThat(imageRecord.filePath()).isEqualTo("/facade_img.jpg");
    assertThat(imageRecord.aspectRatio()).isEqualTo(0.667);
    assertThat(imageRecord.height()).isEqualTo(1800);
    assertThat(imageRecord.width()).isEqualTo(1200);
    assertThat(imageRecord.iso6391()).isNull();
    assertThat(imageRecord.voteAverage()).isEqualTo(7.1);
    assertThat(imageRecord.voteCount()).isEqualTo(25);
    assertThat(mapper.toTmdbProfileImage(null)).isNull();

    var imageRecords = mapper.toTmdbProfileImages(List.of(entity));
    assertThat(imageRecords).hasSize(1);
    assertThat(mapper.toTmdbProfileImages(null)).isNull();
  }

  /**
   * Verifies that {@link ActorMapper#updateEntityFromSummary} applies non-null attributes to an
   * existing entity while preserving existing detailed attributes when summary fields are null.
   */
  @Test
  @DisplayName(
      "updateEntityFromSummary: updates non-null fields and preserves existing detailed fields")
  void updateEntityFromSummaryUpdatesNonNullFields() {
    // Given
    Actor actor =
        Actor.builder()
            .tmdbId(500L)
            .name("Original Name")
            .biography("Detailed biography")
            .birthDate(LocalDate.of(1970, Month.JUNE, 1))
            .birthPlace("London")
            .profilePath("/original.jpg")
            .popularity(40.0)
            .knownForDepartment("Acting")
            .gender(1)
            .adult(false)
            .build();

    // Summary with updated name/popularity but null profilePath and null department
    TmdbPersonSummary summary =
        new TmdbPersonSummary(500L, "Updated Name", null, 95.0, null, 2, true);

    // When
    mapper.updateEntityFromSummary(summary, actor);

    // Then
    assertThat(actor.getName()).isEqualTo("Updated Name");
    assertThat(actor.getPopularity()).isEqualTo(95.0);
    assertThat(actor.getGender()).isEqualTo(2);
    assertThat(actor.getAdult()).isTrue();
    // Null summary fields must NOT overwrite existing entity fields
    assertThat(actor.getProfilePath()).isEqualTo("/original.jpg");
    assertThat(actor.getKnownForDepartment()).isEqualTo("Acting");
    // Detail-only fields must remain completely untouched
    assertThat(actor.getBiography()).isEqualTo("Detailed biography");
    assertThat(actor.getBirthDate()).isEqualTo(LocalDate.of(1970, Month.JUNE, 1));
    assertThat(actor.getBirthPlace()).isEqualTo("London");
  }
}
