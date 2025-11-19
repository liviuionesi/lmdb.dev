package com.filmpire.movie.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Video entity for movie trailers and clips.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Video {
    private String id;
    private String key;
    private String name;
    private String site;
    private Integer size;
    private String type;
    private Boolean official;
    private String publishedAt;
}

