package com.filmpire.movie.dto;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * DTO for Crew member.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrewDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
    private String job;
    private String department;
    private String profilePath;
}
