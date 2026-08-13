package dev.lmdb.movie.model;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Credits entity containing cast and crew.
 *
 * <p>{@link Serializable} for consistency with the rest of the embedded model: any value type that
 * can reach a @Cacheable return value must be serializable, since Redis caching here uses JDK
 * serialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Credits implements Serializable {
  private Long movieId;
  private List<Cast> cast;
  private List<Crew> crew;
}
