package com.filmpire.movie.dto;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * DTO for Cast member.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CastDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
    private String character;
    private String profilePath;
    private Integer order;
}
