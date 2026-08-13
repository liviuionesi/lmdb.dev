package dev.lmdb.ai.service;

import dev.lmdb.ai.dto.SimilarUserDto;
import dev.lmdb.ai.repository.EmbeddingFormat;
import dev.lmdb.ai.repository.UserTasteProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Semantic search over {@link dev.lmdb.ai.model.UserTasteProfile} embeddings: "find users whose
 * taste is closest to this description" — an ANN query run directly against the pgvector index via
 * {@link UserTasteProfileRepository}, with no separate vector database.
 */
@Service
public class SemanticSearchService {

  private final EmbeddingModel embeddingModel;
  private final UserTasteProfileRepository tasteProfileRepository;

  /**
   * @param embeddingModel model used to embed the free-text query
   * @param tasteProfileRepository source of the ANN nearest-neighbour query
   */
  public SemanticSearchService(
      EmbeddingModel embeddingModel, UserTasteProfileRepository tasteProfileRepository) {
    this.embeddingModel = embeddingModel;
    this.tasteProfileRepository = tasteProfileRepository;
  }

  /**
   * Embeds {@code query} and returns the {@code k} nearest taste profiles, excluding the caller.
   *
   * @param query free-text description of a taste ("gritty sci-fi with practical effects")
   * @param requestingUserId the caller, excluded from its own results
   * @param k how many neighbours to return
   * @return the nearest users, closest first
   */
  public List<SimilarUserDto> findSimilarUsers(String query, UUID requestingUserId, int k) {
    float[] queryEmbedding = embeddingModel.embed(query);
    String queryVector = EmbeddingFormat.toPgvectorLiteral(queryEmbedding);

    return tasteProfileRepository.findNearestNeighbours(queryVector, requestingUserId, k).stream()
        .map(row -> new SimilarUserDto((UUID) row[0], ((Number) row[1]).doubleValue()))
        .toList();
  }
}
