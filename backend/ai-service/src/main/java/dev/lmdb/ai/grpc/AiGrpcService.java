package dev.lmdb.ai.grpc;

import dev.lmdb.ai.dto.ChatRequestDto;
import dev.lmdb.ai.dto.ChatResponseDto;
import dev.lmdb.ai.dto.MovieRecommendationDto;
import dev.lmdb.ai.dto.RecommendationRequestDto;
import dev.lmdb.ai.dto.RecommendationResponseDto;
import dev.lmdb.ai.service.ChatAssistantService;
import dev.lmdb.ai.service.RecommendationService;
import dev.lmdb.shared.exception.ResourceNotFoundException;
import dev.lmdb.shared.exception.ServiceUnavailableException;
import dev.lmdb.shared.exception.ValidationException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * gRPC surface for recommendations and chat (defined by {@code ai_service.proto}), delegating to
 * the same {@link RecommendationService} and {@link ChatAssistantService} the REST controller uses
 * — one set of business logic, two transports.
 */
@Service
@Slf4j
public class AiGrpcService extends AIServiceGrpc.AIServiceImplBase {

  private final RecommendationService recommendationService;
  private final ChatAssistantService chatAssistantService;

  /**
   * @param recommendationService the recommendation logic shared with the REST controller
   * @param chatAssistantService the chat logic shared with the REST controller
   */
  public AiGrpcService(
      RecommendationService recommendationService, ChatAssistantService chatAssistantService) {
    this.recommendationService = recommendationService;
    this.chatAssistantService = chatAssistantService;
  }

  /**
   * Handles the {@code GetRecommendations} RPC: validates the request's user id, delegates to
   * {@link RecommendationService}, and translates the result into the gRPC response message.
   *
   * @param request the recommendation request, with {@code user_id} as a string UUID
   * @param responseObserver receives either the ranked recommendations or an {@code
   *     INVALID_ARGUMENT} error if {@code user_id} isn't a valid UUID
   */
  @Override
  public void getRecommendations(
      RecommendationRequest request, StreamObserver<RecommendationResponse> responseObserver) {
    UUID userId;
    try {
      userId = UUID.fromString(request.getUserId());
    } catch (IllegalArgumentException _) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription("user_id must be a UUID").asRuntimeException());
      return;
    }

    try {
      RecommendationResponseDto result =
          recommendationService.recommend(
              new RecommendationRequestDto(
                  userId, request.getRecentMoviesList(), request.getCount()));

      RecommendationResponse.Builder response = RecommendationResponse.newBuilder();
      for (MovieRecommendationDto rec : result.recommendations()) {
        response.addRecommendations(
            MovieRecommendation.newBuilder()
                .setMovieId(rec.movieId())
                .setScore(rec.score())
                .setReason(rec.reason())
                .build());
      }
      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    } catch (ResourceNotFoundException e) {
      responseObserver.onError(
          Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
    } catch (ServiceUnavailableException e) {
      responseObserver.onError(
          Status.UNAVAILABLE.withDescription(e.getMessage()).asRuntimeException());
    } catch (ValidationException | IllegalArgumentException e) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
    } catch (Exception e) {
      log.error("Internal error in getRecommendations", e);
      responseObserver.onError(
          Status.INTERNAL.withDescription("Internal error").asRuntimeException());
    }
  }

  /**
   * Handles the {@code ChatWithAssistant} RPC: validates the request's user and (optional)
   * conversation ids, delegates to {@link ChatAssistantService}, and translates the result into the
   * gRPC response message.
   *
   * @param request the chat request, with {@code user_id} and optional {@code conversation_id} as
   *     string UUIDs
   * @param responseObserver receives either the assistant's reply or an {@code INVALID_ARGUMENT}
   *     error if either id isn't a valid UUID
   */
  @Override
  public void chatWithAssistant(
      ChatRequest request, StreamObserver<ChatResponse> responseObserver) {
    UUID userId;
    UUID conversationId = null;
    try {
      userId = UUID.fromString(request.getUserId());
      if (!request.getConversationId().isEmpty()) {
        conversationId = UUID.fromString(request.getConversationId());
      }
    } catch (IllegalArgumentException _) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT
              .withDescription("user_id/conversation_id must be UUIDs")
              .asRuntimeException());
      return;
    }

    if (request.getMessage().trim().isEmpty()) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription("Message cannot be blank").asRuntimeException());
      return;
    }

    try {
      ChatResponseDto result =
          chatAssistantService.chat(
              new ChatRequestDto(userId, conversationId, request.getMessage()));

      responseObserver.onNext(
          ChatResponse.newBuilder()
              .setConversationId(result.conversationId().toString())
              .setReply(result.reply())
              .build());
      responseObserver.onCompleted();
    } catch (ResourceNotFoundException e) {
      responseObserver.onError(
          Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
    } catch (ServiceUnavailableException e) {
      responseObserver.onError(
          Status.UNAVAILABLE.withDescription(e.getMessage()).asRuntimeException());
    } catch (ValidationException | IllegalArgumentException e) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
    } catch (Exception e) {
      log.error("Internal error in chatWithAssistant", e);
      responseObserver.onError(
          Status.INTERNAL.withDescription("Internal error").asRuntimeException());
    }
  }
}
