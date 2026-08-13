package dev.lmdb.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/ai/chat}.
 *
 * @param userId the requesting user (user-service id)
 * @param conversationId an existing conversation to continue, or {@code null} to start a new one
 * @param message the user's message
 */
public record ChatRequestDto(@NotNull UUID userId, UUID conversationId, @NotBlank String message) {}
