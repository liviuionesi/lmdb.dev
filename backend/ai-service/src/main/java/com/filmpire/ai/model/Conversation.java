package com.filmpire.ai.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A conversation thread with the AI assistant — the persisted, user-owned aggregate root behind the
 * chat and recommendation features. Backed by a Flyway-managed relational schema, with {@link
 * Message} rows cascading as part of the same aggregate.
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** Owning user (user-service's id). No foreign key — cross-service references are id-only. */
  private UUID userId;

  @Enumerated(EnumType.STRING)
  private ConversationType type;

  /** Ordered message thread; cascaded so a conversation is one aggregate. */
  @Builder.Default
  @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("timestamp ASC")
  private List<Message> messages = new ArrayList<>();

  private Instant createdAt;

  private Instant updatedAt;

  /**
   * Appends a message to this conversation's thread, keeping both sides of the bidirectional
   * association in sync and bumping the aggregate's {@code updatedAt}.
   *
   * @param message the message to append; its {@code conversation} back-reference is set here
   */
  public void addMessage(Message message) {
    messages.add(message);
    message.setConversation(this);
    this.updatedAt = Instant.now();
  }
}
