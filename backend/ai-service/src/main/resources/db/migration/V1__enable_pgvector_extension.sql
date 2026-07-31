-- V1: enable the pgvector extension (ADR-012). Requires the shared Postgres
-- container to run pgvector/pgvector:pg17, not stock postgres:17-alpine —
-- see infrastructure/docker/docker-compose.yml and the K8s postgres base.
CREATE EXTENSION IF NOT EXISTS vector;
