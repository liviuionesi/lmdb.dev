package dev.lmdb.movie.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cast member entity.
 *
 * <p>{@link Serializable} for consistency with the rest of the embedded model: any value type that
 * can reach a @Cacheable return value must be serializable, since Redis caching here uses JDK
 * serialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cast implements Serializable {
  private Long id;
  private String name;
  private String character;
  private String profilePath;
  private Integer order;
  private Long castId;
}
