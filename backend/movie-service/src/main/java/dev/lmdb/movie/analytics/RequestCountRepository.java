package dev.lmdb.movie.analytics;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * MongoDB repository for {@link RequestCount} analytics counters.
 *
 * <p>Provides ordered retrieval used by the most-requested analytics endpoint. Custom upsert
 * operations that increment atomically are performed via {@link
 * org.springframework.data.mongodb.core.MongoTemplate} in {@link TmdbAnalyticsConsumer}, keeping
 * this interface lightweight.
 */
public interface RequestCountRepository extends MongoRepository<RequestCount, String> {

  /**
   * Returns all request-count documents sorted by descending count, supporting paginated most-
   * requested views.
   *
   * @param pageable pagination and sort configuration
   * @return ordered list of {@link RequestCount} documents
   */
  List<RequestCount> findAllByOrderByCountDesc(org.springframework.data.domain.Pageable pageable);
}
