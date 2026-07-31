package com.filmpire.ai.repository;

import com.filmpire.ai.model.UserTasteProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for {@link UserTasteProfile}, including the ANN
 * (approximate-nearest-neighbour) semantic-search query over its pgvector
 * {@code embedding} column (#36, ARCHITECTURE.md §3.7).
 */
public interface UserTasteProfileRepository extends JpaRepository<UserTasteProfile, UUID> {

    /**
     * Finds the {@code k} taste profiles whose embedding is closest (cosine
     * distance, backed by the {@code idx_user_taste_profiles_embedding}
     * HNSW index) to {@code queryVector} — the ANN query ARCHITECTURE.md
     * §3.7 specifies for semantic search.
     *
     * <p>{@code queryVector} is pgvector's text input format
     * ({@code "[0.1,0.2,...]"}, see {@code EmbeddingFormat}) bound as a
     * parameter and cast in SQL, rather than relying on JDBC array binding
     * for a native query — the same representation the driver would produce
     * either way, made explicit.</p>
     *
     * @param queryVector    the query embedding, pgvector text format
     * @param excludeUserId  a user to exclude from results (typically the
     *                       caller, so "similar users" never returns yourself)
     * @param limit          maximum number of neighbours to return
     * @return the nearest profiles, closest first
     */
    @Query(
        value = """
            SELECT * FROM user_taste_profiles
            WHERE user_id != :excludeUserId
            ORDER BY embedding <=> CAST(:queryVector AS vector)
            LIMIT :limit
            """,
        nativeQuery = true
    )
    List<UserTasteProfile> findNearestNeighbours(
        @Param("queryVector") String queryVector,
        @Param("excludeUserId") UUID excludeUserId,
        @Param("limit") int limit
    );
}
