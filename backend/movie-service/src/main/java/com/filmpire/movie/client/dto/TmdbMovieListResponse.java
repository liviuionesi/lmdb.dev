package com.filmpire.movie.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * TMDB API response for movie lists (discover, search, popular, etc.).
 */
@Data
public class TmdbMovieListResponse {
    private Integer page;
    
    @JsonProperty("total_pages")
    private Integer totalPages;
    
    @JsonProperty("total_results")
    private Integer totalResults;
    
    private List<TmdbMovieItem> results;
    
    @Data
    public static class TmdbMovieItem {
        private Long id;
        private String title;
        private String overview;
        
        @JsonProperty("poster_path")
        private String posterPath;
        
        @JsonProperty("backdrop_path")
        private String backdropPath;
        
        @JsonProperty("release_date")
        private String releaseDate;
        
        @JsonProperty("vote_average")
        private Double voteAverage;
        
        @JsonProperty("vote_count")
        private Integer voteCount;
        
        @JsonProperty("genre_ids")
        private List<Long> genreIds;
        
        private Double popularity;
        private Boolean adult;
        
        @JsonProperty("original_language")
        private String originalLanguage;
    }
}

