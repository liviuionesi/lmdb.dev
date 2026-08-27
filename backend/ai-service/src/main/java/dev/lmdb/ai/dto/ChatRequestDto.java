package dev.lmdb.ai.dto;

import java.util.UUID;

/**
 * Service-layer chat request handed to {@link dev.lmdb.ai.service.ChatAssistantService}, assembled
 * by whichever transport received the call: the REST controller combines {@link ChatRequestBodyDto}
 * with the authenticated caller from the {@code X-User-Id} header, and {@link
 * dev.lmdb.ai.grpc.AiGrpcService} builds it from the proto message.
 *
 * <p>This is not a request body and carries no bean-validation constraints — each transport is
 * responsible for validating its own input before constructing one. {@code userId} is therefore
 * always an already-authenticated identity by the time it reaches here, never a caller-supplied
 * one.
 *
 * @param userId the authenticated user this conversation belongs to
 * @param conversationId an existing conversation to continue, or {@code null} to start a new one
 * @param message the user's message
 */
public record ChatRequestDto(UUID userId, UUID conversationId, String message) {}
