package com.filmpire.movie.mapper;

import com.filmpire.movie.dto.*;
import com.filmpire.movie.model.*;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MapStruct mapper for Movie entity and DTOs.
 */
@Mapper(componentModel = "spring")
public interface MovieMapper {

    /**
     * Convert Movie entity to MovieDto.
     */
    MovieDto toDto(Movie movie);

    /**
     * Convert Movie entity to MovieListDto.
     */
    MovieListDto toListDto(Movie movie);

    /**
     * Convert list of Movie entities to list of MovieListDto.
     */
    List<MovieListDto> toListDto(List<Movie> movies);

    /**
     * Convert Genre to GenreDto.
     */
    GenreDto toDto(Genre genre);

    /**
     * Convert list of Genres to list of GenreDtos.
     */
    List<GenreDto> genresToDto(List<Genre> genres);

    /**
     * Convert Video to VideoDto.
     */
    VideoDto toDto(Video video);

    /**
     * Convert list of Videos to list of VideoDtos.
     */
    List<VideoDto> videosToDto(List<Video> videos);

    /**
     * Convert Cast to CastDto.
     */
    CastDto toDto(Cast cast);

    /**
     * Convert list of Cast to list of CastDtos.
     */
    List<CastDto> castToDto(List<Cast> cast);

    /**
     * Convert Crew to CrewDto.
     */
    CrewDto toDto(Crew crew);

    /**
     * Convert list of Crew to list of CrewDtos.
     */
    List<CrewDto> crewToDto(List<Crew> crew);

    /**
     * Convert Credits to CreditsDto.
     */
    CreditsDto toDto(Credits credits);
}

