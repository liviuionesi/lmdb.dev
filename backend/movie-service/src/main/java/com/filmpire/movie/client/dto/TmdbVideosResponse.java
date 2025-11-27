package com.filmpire.movie.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * TMDB API response for movie videos (trailers, clips).
 */
public record TmdbVideosResponse(
    Long id,
    List<TmdbVideo> results
) {
    public record TmdbVideo(
        String id,
        String key,
        String name,
        String site,
        Integer size,
        String type,
        Boolean official,
        @JsonProperty("published_at") String publishedAt
    ) {}
}
