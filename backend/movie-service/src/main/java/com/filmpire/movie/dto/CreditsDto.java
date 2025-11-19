package com.filmpire.movie.dto;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
/**
 * DTO for Credits (cast and crew).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditsDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long movieId;
    private List<CastDto> cast;
    private List<CrewDto> crew;
}
