-- V3: persist an actor's full profile-image set so GET /person/{id}/images is
-- served from actor-service's own data (ADR-010), not proxied to TMDB.
--
-- Only TMDB's CDN *reference* (file_path) plus its metadata is stored — the
-- image bytes are never downloaded (ARCHITECTURE.md §3.8); the client resolves
-- file_path against image.tmdb.org exactly as it does against the native API.

CREATE TABLE actor_profile_images (
    actor_tmdb_id BIGINT       NOT NULL REFERENCES actors (tmdb_id),
    file_path     VARCHAR(255) NOT NULL,
    aspect_ratio  DOUBLE PRECISION,
    height        INTEGER,
    width         INTEGER,
    iso_639_1     VARCHAR(10),
    vote_average  DOUBLE PRECISION,
    vote_count    INTEGER
);

CREATE INDEX idx_actor_profile_images_actor ON actor_profile_images (actor_tmdb_id);
