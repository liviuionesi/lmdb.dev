# ADR-014: Media Service Storage & S3/MinIO Object Architecture

**Status:** Accepted  
**Date:** 2026-08-04  
**Deciders:** Project owner  

## Context

Task #37 introduced user-uploaded media capabilities, including custom user profile avatars (#116) and rich review image attachments (#117). 

TMDB media (posters, backdrops, stills) is served via TMDB's public CDN, but user-generated media (avatars, attachments) requires dedicated binary file storage and metadata tracking.

## Decision

`media-service` is established as a dedicated microservice with dual-tier storage:
1. **S3-Compatible Object Store (MinIO / AWS S3)**: Stores raw image payload bytes with SHA-256 deduplication keys and content-type metadata.
2. **MongoDB Metadata Database (`lmdb_media`)**: Stores document metadata tracking upload ownership (`userId`), media entity type (`AVATAR` vs `REVIEW_ATTACHMENT`), image dimensions, content length, S3 object keys, and upload timestamps.

### Key Architectural Standards
- **API Gateway Routing**: Requests to `/api/v1/media/**` are routed to `media-service` with circuit breaker and rate limiting enabled (`mediaServiceCircuitBreaker`).
- **Null Safety & Validation**: Strict input sanitization and non-null validation on upload streams to prevent corrupt S3 object key creation or orphan database metadata.
- **Microservice Independence**: `media-service` exposes clean REST APIs for file upload and streaming. Other services (`user-service`, `movie-service`) store media reference URLs rather than binary payloads.

## Consequences

- **Positive**: Clean separation of binary file storage from relational/document databases; scalable S3 object storage; reusable across profile avatars and review attachments.
- **Negative**: Requires local MinIO instance or S3 bucket credentials during development and integration testing.
