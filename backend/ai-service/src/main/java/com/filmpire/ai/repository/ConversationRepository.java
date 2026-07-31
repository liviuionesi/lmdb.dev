package com.filmpire.ai.repository;

import com.filmpire.ai.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link Conversation} aggregates. Messages are never
 * queried independently — they load with their owning conversation via the
 * {@code @OneToMany} mapping (ADR-012's aggregate).
 */
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Looks up a conversation scoped to its owner, so one user can never
     * read or append to another user's conversation by guessing an id.
     *
     * @param id     the conversation id
     * @param userId the expected owning user
     * @return the conversation if it exists and belongs to {@code userId}
     */
    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);
}
