package com.filmpire.ai.dto;

import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/ai/chat}.
 *
 * @param conversationId the conversation this reply belongs to (echoes the request's id, or the
 *     newly created one)
 * @param reply the assistant's reply
 */
public record ChatResponseDto(UUID conversationId, String reply) {}
