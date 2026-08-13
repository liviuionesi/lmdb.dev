package dev.lmdb.ai.model;

/**
 * The feature a {@link Conversation} belongs to. Persisted as the conversation's {@code type}
 * column (see V2__init_ai_schema.sql).
 */
public enum ConversationType {
  /** A back-and-forth conversation with the chat assistant. */
  CHAT,
  /** The exchange that produced a set of movie recommendations. */
  RECOMMENDATION
}
