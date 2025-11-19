package com.filmpire.movie.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * TMDB API response for movie videos (trailers, clips).
 */
@Data
public class TmdbVideosResponse {
    private Long id;
    private List<TmdbVideo> results;
    
    @Data
    public static class TmdbVideo {
        private String id;
        private String key;
        private String name;
        private String site;
        private Integer size;
        private String type;
        private Boolean official;
        
        @JsonProperty("published_at")
        private String publishedAt;
    }
}

