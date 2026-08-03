package com.filmpire.ai.dto;

import java.util.UUID;

/**
 * One semantic-search result: a user whose taste embedding is near the query, and how near.
 *
 * @param userId the neighbouring user
 * @param distance cosine distance to the query (smaller is closer)
 */
public record SimilarUserDto(UUID userId, double distance) {}
