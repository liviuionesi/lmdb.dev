package com.filmpire.movie.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.filmpire.movie.model.Genre;
import com.filmpire.movie.model.ProductionCompany;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * TMDB API response for movie details.
 */
@Data
public class TmdbMovieResponse {
    private Long id;
    private String title;
    private String overview;
    
    @JsonProperty("poster_path")
    private String posterPath;
    
    @JsonProperty("backdrop_path")
    private String backdropPath;
    
    @JsonProperty("release_date")
    private LocalDate releaseDate;
    
    @JsonProperty("vote_average")
    private Double voteAverage;
    
    @JsonProperty("vote_count")
    private Integer voteCount;
    
    private List<Genre> genres;
    private Integer runtime;
    private String status;
    private Long budget;
    private Long revenue;
    
    @JsonProperty("spoken_languages")
    private List<SpokenLanguage> spokenLanguages;
    
    @JsonProperty("production_companies")
    private List<ProductionCompany> productionCompanies;
    
    @JsonProperty("original_language")
    private String originalLanguage;
    
    private Double popularity;
    private Boolean adult;
    
    @JsonProperty("imdb_id")
    private String imdbId;
    
    private String tagline;
    private String homepage;
    
    @Data
    public static class SpokenLanguage {
        @JsonProperty("iso_639_1")
        private String iso6391;
        private String name;
    }
}

