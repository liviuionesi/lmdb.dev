package dev.lmdb.ai.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/ai/chat}.
 *
 * <p>Deliberately carries no user id: the caller's identity comes from the gateway-issued {@code
 * X-User-Id} header ({@link dev.lmdb.ai.security.CallerIdentity}), never from the body, so a caller
 * cannot name a user other than itself. {@link ChatRequestDto} is the service-layer request this is
 * combined with that header to produce.
 *
 * @param conversationId an existing conversation to continue, or {@code null} to start a new one;
 *     ownership is enforced against the authenticated caller, not against this value
 * @param message the user's message
 */
public record ChatRequestBodyDto(UUID conversationId, @NotBlank String message) {}
