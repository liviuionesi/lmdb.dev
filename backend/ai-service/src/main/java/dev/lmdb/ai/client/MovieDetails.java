package dev.lmdb.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The fields of one movie's details that a search needs, as movie-service returns them.
 *
 * @param tmdbId TMDB movie id
 * @param title movie title
 * @param overview short synopsis, may be null
 * @param releaseDate release date such as {@code 2006-11-14}, may be null
 * @param posterPath TMDB poster path, may be null
 * @param voteAverage average vote, may be null
 * @param revenue box-office revenue in dollars, may be null or zero when unknown
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MovieDetails(
    Long tmdbId,
    String title,
    String overview,
    String releaseDate,
    String posterPath,
    Double voteAverage,
    Long revenue) {}
