-- V2: initial ai-service schema (issue #36, ARCHITECTURE.md §3.7, ADR-012).
--
-- Conversations/messages are relational and Flyway-managed like every other
-- piece of user-owned data in this system (user-service accounts/favorites,
-- actor-service's synced projection) — NOT MongoDB, and NOT covered by
-- ADR-011's self-healing, because conversation history cannot be
-- re-derived if a document stops matching the model.
--
-- user_id has no foreign key on purpose: ADR-002 forbids cross-service
-- joins. user-service owns that identity.

CREATE TABLE conversations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    type        VARCHAR(32) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_user_id ON conversations (user_id);

CREATE TABLE messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role             VARCHAR(16) NOT NULL,
    content          TEXT NOT NULL,
    "timestamp"      TIMESTAMP NOT NULL DEFAULT now(),
    -- Provider-specific fields whose shape genuinely varies — structured
    -- where structure exists (the columns above), flexible where it
    -- doesn't (ADR-012).
    metadata         JSONB
);

CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);

-- Dimension 768 matches Ollama's nomic-embed-text model (see
-- application.yml spring.ai.ollama.embedding.options.model). Changing the
-- embedding model requires a new migration to alter this column.
CREATE TABLE user_taste_profiles (
    user_id         UUID PRIMARY KEY,
    embedding       vector(768) NOT NULL,
    feature_weights JSONB,
    last_updated    TIMESTAMP NOT NULL DEFAULT now()
);

-- HNSW + cosine distance backs the semantic-search ANN query
-- ("ORDER BY embedding <=> :query LIMIT :k" — ARCHITECTURE.md §3.7).
CREATE INDEX idx_user_taste_profiles_embedding
    ON user_taste_profiles
    USING hnsw (embedding vector_cosine_ops);
