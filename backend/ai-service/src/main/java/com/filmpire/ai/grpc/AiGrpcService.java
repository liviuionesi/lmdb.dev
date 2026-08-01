package com.filmpire.ai.grpc;

import com.filmpire.ai.dto.ChatRequestDto;
import com.filmpire.ai.dto.ChatResponseDto;
import com.filmpire.ai.dto.MovieRecommendationDto;
import com.filmpire.ai.dto.RecommendationRequestDto;
import com.filmpire.ai.dto.RecommendationResponseDto;
import com.filmpire.ai.service.ChatAssistantService;
import com.filmpire.ai.service.RecommendationService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * gRPC surface for recommendations and chat (#36, ai_service.proto),
 * delegating to the same {@link RecommendationService} and
 * {@link ChatAssistantService} the REST controller uses — one set of
 * business logic, two transports.
 */
@Component
@Slf4j
public class AiGrpcService extends AIServiceGrpc.AIServiceImplBase {

    private final RecommendationService recommendationService;
    private final ChatAssistantService chatAssistantService;

    public AiGrpcService(RecommendationService recommendationService, ChatAssistantService chatAssistantService) {
        this.recommendationService = recommendationService;
        this.chatAssistantService = chatAssistantService;
    }

    @Override
    public void getRecommendations(
        RecommendationRequest request,
        StreamObserver<RecommendationResponse> responseObserver
    ) {
        UUID userId;
        try {
            userId = UUID.fromString(request.getUserId());
        } catch (IllegalArgumentException _) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT.withDescription("user_id must be a UUID").asRuntimeException());
            return;
        }

        RecommendationResponseDto result = recommendationService.recommend(
            new RecommendationRequestDto(userId, request.getRecentMoviesList(), request.getCount()));

        RecommendationResponse.Builder response = RecommendationResponse.newBuilder();
        for (MovieRecommendationDto rec : result.recommendations()) {
            response.addRecommendations(MovieRecommendation.newBuilder()
                .setMovieId(rec.movieId())
                .setScore(rec.score())
                .setReason(rec.reason())
                .build());
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void chatWithAssistant(ChatRequest request, StreamObserver<ChatResponse> responseObserver) {
        UUID userId;
        UUID conversationId = null;
        try {
            userId = UUID.fromString(request.getUserId());
            if (!request.getConversationId().isEmpty()) {
                conversationId = UUID.fromString(request.getConversationId());
            }
        } catch (IllegalArgumentException _) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT.withDescription("user_id/conversation_id must be UUIDs").asRuntimeException());
            return;
        }

        ChatResponseDto result = chatAssistantService.chat(
            new ChatRequestDto(userId, conversationId, request.getMessage()));

        responseObserver.onNext(ChatResponse.newBuilder()
            .setConversationId(result.conversationId().toString())
            .setReply(result.reply())
            .build());
        responseObserver.onCompleted();
    }
}
