package com.filmpire.movie.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;

import java.io.Serializable;

/**
 * Genre entity representing a movie genre. Embedded on {@link Movie}; the
 * index on {@link #id} backs {@code MovieRepository.findByGenreId}'s
 * {@code 'genres.id'} query. Serializable: reaches Redis via the facade's
 * cached {@code TmdbGenresResponse}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Genre implements Serializable {
    @Indexed
    private Long id;
    private String name;
}

