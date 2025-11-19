package com.filmpire.movie.client.dto;

import com.filmpire.movie.model.Genre;
import lombok.Data;

import java.util.List;

/**
 * TMDB API response for genres list.
 */
@Data
public class TmdbGenresResponse {
    private List<Genre> genres;
}

