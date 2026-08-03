package com.filmpire.actor.mapper;

import com.filmpire.actor.client.dto.TmdbPersonResponse;
import com.filmpire.actor.client.dto.TmdbPersonSearchResponse.TmdbPersonSummary;
import com.filmpire.actor.dto.ActorDtos.ActorDto;
import com.filmpire.actor.dto.ActorDtos.ActorSummaryDto;
import com.filmpire.actor.model.Actor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
}
