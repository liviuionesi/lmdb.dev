package com.filmpire.movie.dto;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * DTO for Genre.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenreDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
}
