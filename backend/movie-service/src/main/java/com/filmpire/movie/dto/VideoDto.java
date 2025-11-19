package com.filmpire.movie.dto;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * DTO for Video (trailers, clips).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private String key;
    private String name;
    private String site;
    private Integer size;
    private String type;
    private Boolean official;
    private String publishedAt;
}
