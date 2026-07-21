-- V1: initial actor-service schema (issues #18/#32, ARCHITECTURE.md §3.6)
--
-- tmdb_person_documents.json is TEXT, not JSONB, on purpose: JSONB
-- normalizes key order / number formats, which would break the byte-for-byte
-- TMDB facade contract (ADR-003). The typed 'actors' table is the queryable
-- projection populated as a side effect of native API fetches.

CREATE TABLE tmdb_person_documents (
    id          VARCHAR(512) PRIMARY KEY,
    json        TEXT      NOT NULL,
    fetched_at  TIMESTAMP NOT NULL
);

CREATE TABLE actors (
    tmdb_id       BIGINT PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    biography     TEXT,
    birth_date    DATE,
    birth_place   VARCHAR(255),
    profile_path  VARCHAR(255),
    popularity    DOUBLE PRECISION,
    synced_at     TIMESTAMP NOT NULL
);

-- Name lookups back the (future) local search over the synced dataset.
CREATE INDEX idx_actors_name ON actors (name);
