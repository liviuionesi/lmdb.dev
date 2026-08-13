package dev.lmdb.movie.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Video entity for movie trailers and clips.
 *
 * <p>{@link Serializable} for consistency with the rest of the embedded model: any value type that
 * can reach a @Cacheable return value must be serializable, since Redis caching here uses JDK
 * serialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Video implements Serializable {
  private String id;
  private String key;
  private String name;
  private String site;
  private Integer size;
  private String type;
  private Boolean official;
  private String publishedAt;
}
