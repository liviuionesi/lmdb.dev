package dev.lmdb.ai.repository;

import dev.lmdb.ai.model.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link Conversation} aggregates. Messages are never queried independently — they
 * load with their owning conversation via the {@code @OneToMany} mapping.
 */
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  /**
   * Looks up a conversation scoped to its owner, so one user can never read or append to another
   * user's conversation by guessing an id.
   *
   * @param id the conversation id
   * @param userId the expected owning user
   * @return the conversation if it exists and belongs to {@code userId}
   */
  @EntityGraph(attributePaths = "messages")
  Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);
}
