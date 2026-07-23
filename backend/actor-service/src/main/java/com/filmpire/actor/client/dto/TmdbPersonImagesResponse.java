package com.filmpire.actor.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

/**
 * TMDB API response for {@code /person/{id}/images}.
 *
 * <p>Serializable because the service layer caches it in Redis, which is
 * configured for JDK serialization in this project — a cached type without
 * {@code Serializable} fails at runtime, not compile time.</p>
 *
 * @param id       TMDB person id the images belong to
 * @param profiles every profile image TMDB holds for that person
 */
public record TmdbPersonImagesResponse(
    Long id,
    List<TmdbProfileImage> profiles
) implements Serializable {

    /**
     * A single profile image: a TMDB CDN path plus its metadata. The bytes are
     * never fetched or stored (ARCHITECTURE.md §3.8).
     *
     * @param filePath    TMDB CDN path, resolved client-side against image.tmdb.org
     * @param aspectRatio width divided by height
     * @param height      pixel height
     * @param width       pixel width
     * @param iso6391     ISO 639-1 language tag, null when language-neutral
     * @param voteAverage TMDB community vote average
     * @param voteCount   TMDB community vote count
     */
    public record TmdbProfileImage(
        @JsonProperty("file_path") String filePath,
        @JsonProperty("aspect_ratio") Double aspectRatio,
        Integer height,
        Integer width,
        @JsonProperty("iso_639_1") String iso6391,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("vote_count") Integer voteCount
    ) implements Serializable {
    }
}
