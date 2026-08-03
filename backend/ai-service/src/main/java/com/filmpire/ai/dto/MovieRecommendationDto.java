package com.filmpire.ai.dto;

/**
 * One recommended movie, scored and explained by the assistant.
 *
 * @param movieId TMDB id of the recommended movie (as a string, matching the gRPC contract)
 * @param score similarity/confidence score in {@code [0, 1]}
 * @param reason a short, human-readable explanation of why it was recommended
 */
public record MovieRecommendationDto(String movieId, double score, String reason) {}
