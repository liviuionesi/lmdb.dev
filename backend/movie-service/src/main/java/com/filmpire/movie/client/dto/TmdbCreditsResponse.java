package com.filmpire.movie.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * TMDB API response for movie credits (cast and crew).
 */
@Data
public class TmdbCreditsResponse {
    private Long id;
    private List<TmdbCast> cast;
    private List<TmdbCrew> crew;
    
    @Data
    public static class TmdbCast {
        private Long id;
        private String name;
        private String character;
        
        @JsonProperty("profile_path")
        private String profilePath;
        
        private Integer order;
        
        @JsonProperty("cast_id")
        private Long castId;
    }
    
    @Data
    public static class TmdbCrew {
        private Long id;
        private String name;
        private String job;
        private String department;
        
        @JsonProperty("profile_path")
        private String profilePath;
        
        @JsonProperty("credit_id")
        private String creditId;
    }
}

