-- V2: actor-service TMDB facade serves mapped, persisted data (ADR-010,
-- supersedes ADR-003). Drops the raw-passthrough store and closes the
-- 'actors' table's field gaps against TMDB's real /person/{id} shape.

DROP TABLE tmdb_person_documents;

ALTER TABLE actors
    ADD COLUMN known_for_department VARCHAR(100),
    ADD COLUMN gender               INTEGER,
    ADD COLUMN imdb_id              VARCHAR(20),
    ADD COLUMN homepage             VARCHAR(512),
    ADD COLUMN adult                BOOLEAN;

CREATE TABLE actor_also_known_as (
    actor_tmdb_id  BIGINT       NOT NULL REFERENCES actors (tmdb_id),
    also_known_as  VARCHAR(255) NOT NULL
);

CREATE INDEX idx_actor_also_known_as_actor ON actor_also_known_as (actor_tmdb_id);
