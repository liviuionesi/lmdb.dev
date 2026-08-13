package dev.lmdb.movie.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Crew member entity.
 *
 * <p>{@link Serializable} for consistency with the rest of the embedded model: any value type that
 * can reach a @Cacheable return value must be serializable, since Redis caching here uses JDK
 * serialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Crew implements Serializable {
  private Long id;
  private String name;
  private String job;
  private String department;
  private String profilePath;
  private Long creditId;
}
