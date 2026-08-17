package dev.lmdb.actor.mapper;

import dev.lmdb.actor.client.dto.TmdbPersonImagesResponse.TmdbProfileImage;
import dev.lmdb.actor.client.dto.TmdbPersonMovieCreditsResponse.TmdbCastCredit;
import dev.lmdb.actor.client.dto.TmdbPersonResponse;
import dev.lmdb.actor.client.dto.TmdbPersonSearchResponse.TmdbPersonSummary;
import dev.lmdb.actor.dto.ActorDtos.ActorDto;
import dev.lmdb.actor.dto.ActorDtos.ActorImageDto;
import dev.lmdb.actor.dto.ActorDtos.ActorSummaryDto;
import dev.lmdb.actor.dto.ActorDtos.FilmographyEntryDto;
import dev.lmdb.actor.model.Actor;
import dev.lmdb.actor.model.ActorProfileImage;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper encapsulating all conversions between TMDB API records, domain {@link Actor}
 * entities, and native API DTOs.
 *
 * <p>Generates type-safe mapping code at build time, replacing manual field-by-field copying while
 * preserving zero-reflection execution speed.
 */
@Mapper(componentModel = "spring")
public interface ActorMapper {

  /**
   * Converts a domain {@link Actor} entity into a native {@link ActorDto}.
   *
   * @param actor entity
   * @return DTO representation
   */
  ActorDto toDto(Actor actor);

  /**
   * Converts a TMDB person detail response into an unpersisted domain {@link Actor} entity.
   *
   * @param response TMDB person detail record
   * @return domain entity
   */
  @Mapping(source = "id", target = "tmdbId")
  @Mapping(source = "birthday", target = "birthDate")
  @Mapping(source = "placeOfBirth", target = "birthPlace")
  @Mapping(target = "biography", defaultValue = "")
  @Mapping(target = "profileImages", ignore = true)
  @Mapping(target = "syncedAt", expression = "java(java.time.LocalDateTime.now())")
  Actor toEntity(TmdbPersonResponse response);

  /**
   * Converts a domain {@link Actor} entity into a TMDB person detail response shape for facade
   * serialization.
   *
   * @param actor entity
   * @return TMDB response record shape
   */
  @Mapping(source = "tmdbId", target = "id")
  @Mapping(source = "birthDate", target = "birthday")
  @Mapping(source = "birthPlace", target = "placeOfBirth")
  TmdbPersonResponse toTmdbPersonResponse(Actor actor);

  /**
   * Maps a single search result summary to a native {@link ActorSummaryDto}.
   *
   * @param summary TMDB summary record
   * @return native summary DTO
   */
  @Mapping(source = "id", target = "tmdbId")
  ActorSummaryDto toSummaryDto(TmdbPersonSummary summary);

  /**
   * Maps a list of TMDB search result summaries to a list of native {@link ActorSummaryDto}s.
   *
   * @param summaries TMDB summary records
   * @return native summary DTOs
   */
  List<ActorSummaryDto> toSummaryDtos(List<TmdbPersonSummary> summaries);

  /**
   * Maps a single TMDB cast credit to a native {@link FilmographyEntryDto}.
   *
   * @param credit TMDB cast credit record
   * @return native filmography entry DTO
   */
  @Mapping(source = "id", target = "movieId")
  @Mapping(target = "releaseDate", defaultValue = "")
  FilmographyEntryDto toFilmographyEntryDto(TmdbCastCredit credit);

  /**
   * Maps a list of TMDB cast credits to a list of native {@link FilmographyEntryDto}s.
   *
   * @param credits TMDB cast credit records
   * @return native filmography entry DTOs
   */
  List<FilmographyEntryDto> toFilmographyEntryDtos(List<TmdbCastCredit> credits);

  /**
   * Maps a domain {@link ActorProfileImage} to a native {@link ActorImageDto}.
   *
   * @param image domain profile image value object
   * @return native image DTO
   */
  ActorImageDto toImageDto(ActorProfileImage image);

  /**
   * Maps a list of domain {@link ActorProfileImage}s to a list of native {@link ActorImageDto}s.
   *
   * @param images domain profile image value objects
   * @return native image DTOs
   */
  List<ActorImageDto> toImageDtos(List<ActorProfileImage> images);

  /**
   * Maps a TMDB profile image response record to a domain {@link ActorProfileImage} value object.
   *
   * @param profile TMDB profile image record
   * @return domain profile image value object
   */
  ActorProfileImage toProfileImageEntity(TmdbProfileImage profile);

  /**
   * Maps a list of TMDB profile image response records to a list of domain {@link
   * ActorProfileImage}s.
   *
   * @param profiles TMDB profile image records
   * @return domain profile image value objects
   */
  List<ActorProfileImage> toProfileImageEntities(List<TmdbProfileImage> profiles);

  /**
   * Maps a domain {@link ActorProfileImage} to a TMDB facade {@link TmdbProfileImage} record.
   *
   * @param image domain profile image value object
   * @return TMDB profile image record
   */
  TmdbProfileImage toTmdbProfileImage(ActorProfileImage image);

  /**
   * Maps a list of domain {@link ActorProfileImage}s to a list of TMDB facade {@link
   * TmdbProfileImage} records.
   *
   * @param images domain profile image value objects
   * @return TMDB profile image records
   */
  List<TmdbProfileImage> toTmdbProfileImages(List<ActorProfileImage> images);

  /**
   * Partially updates an existing {@link Actor} entity from a search summary stub, ignoring null
   * source fields so that existing detailed profile fields (biography, birth date, etc.) are left
   * untouched.
   *
   * @param summary search summary record (source)
   * @param actor domain entity to update in place (target)
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(source = "id", target = "tmdbId")
  @Mapping(target = "syncedAt", ignore = true)
  @Mapping(target = "biography", ignore = true)
  @Mapping(target = "birthDate", ignore = true)
  @Mapping(target = "birthPlace", ignore = true)
  @Mapping(target = "alsoKnownAs", ignore = true)
  @Mapping(target = "profileImages", ignore = true)
  @Mapping(target = "imdbId", ignore = true)
  @Mapping(target = "homepage", ignore = true)
  void updateEntityFromSummary(TmdbPersonSummary summary, @MappingTarget Actor actor);
}
