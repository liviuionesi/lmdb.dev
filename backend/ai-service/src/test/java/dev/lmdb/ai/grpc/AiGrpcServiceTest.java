package dev.lmdb.ai.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.lmdb.ai.dto.ChatRequestDto;
import dev.lmdb.ai.dto.ChatResponseDto;
import dev.lmdb.ai.dto.MovieRecommendationDto;
import dev.lmdb.ai.dto.RecommendationRequestDto;
import dev.lmdb.ai.dto.RecommendationResponseDto;
import dev.lmdb.ai.service.ChatAssistantService;
import dev.lmdb.ai.service.RecommendationService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link AiGrpcService}, the gRPC-to-REST-service adapter. {@link
 * RecommendationService} and {@link ChatAssistantService} are Mockito mocks — the business logic
 * they contain is exercised elsewhere ({@code AiServiceIntegrationTest}); what's under test here is
 * this class's own responsibility: UUID validation and request/response translation between the
 * proto messages and the internal DTOs.
 */
@DisplayName("AiGrpcService (gRPC adapter)")
class AiGrpcServiceTest {

  private RecommendationService recommendationService;
  private ChatAssistantService chatAssistantService;
  private AiGrpcService aiGrpcService;

  @BeforeEach
  void setUp() {
    recommendationService = mock(RecommendationService.class);
    chatAssistantService = mock(ChatAssistantService.class);
    aiGrpcService = new AiGrpcService(recommendationService, chatAssistantService);
  }

  /**
   * Given a valid {@code user_id}, when {@code getRecommendations} is called, then the request is
   * delegated to {@link RecommendationService} and the result is translated into a {@link
   * RecommendationResponse} with one entry per {@link MovieRecommendationDto}.
   */
  @Test
  @DisplayName("getRecommendations delegates and translates the response")
  void getRecommendationsDelegatesAndTranslates() {
    UUID userId = UUID.randomUUID();
    RecommendationRequest request =
        RecommendationRequest.newBuilder()
            .setUserId(userId.toString())
            .addRecentMovies("Inception")
            .setCount(5)
            .build();
    when(recommendationService.recommend(any()))
        .thenReturn(
            new RecommendationResponseDto(
                List.of(new MovieRecommendationDto("42", 0.9, "similar taste"))));
    @SuppressWarnings("unchecked")
    StreamObserver<RecommendationResponse> responseObserver = mock(StreamObserver.class);

    aiGrpcService.getRecommendations(request, responseObserver);

    ArgumentCaptor<RecommendationRequestDto> requestCaptor =
        ArgumentCaptor.forClass(RecommendationRequestDto.class);
    verify(recommendationService).recommend(requestCaptor.capture());
    assertThat(requestCaptor.getValue().userId()).isEqualTo(userId);
    assertThat(requestCaptor.getValue().recentMovies()).containsExactly("Inception");
    assertThat(requestCaptor.getValue().count()).isEqualTo(5);

    ArgumentCaptor<RecommendationResponse> responseCaptor =
        ArgumentCaptor.forClass(RecommendationResponse.class);
    verify(responseObserver).onNext(responseCaptor.capture());
    verify(responseObserver).onCompleted();
    verify(responseObserver, never()).onError(any());
    MovieRecommendation recommendation = responseCaptor.getValue().getRecommendations(0);
    assertThat(recommendation.getMovieId()).isEqualTo("42");
    assertThat(recommendation.getScore()).isEqualTo(0.9);
    assertThat(recommendation.getReason()).isEqualTo("similar taste");
  }

  /**
   * Given a {@code user_id} that isn't a valid UUID, when {@code getRecommendations} is called,
   * then the observer receives an {@code INVALID_ARGUMENT} error instead of reaching {@link
   * RecommendationService}.
   */
  @Test
  @DisplayName("getRecommendations rejects a non-UUID user_id without calling the service")
  void getRecommendationsRejectsInvalidUserId() {
    RecommendationRequest request =
        RecommendationRequest.newBuilder().setUserId("not-a-uuid").build();
    @SuppressWarnings("unchecked")
    StreamObserver<RecommendationResponse> responseObserver = mock(StreamObserver.class);

    aiGrpcService.getRecommendations(request, responseObserver);

    ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
    verify(responseObserver).onError(errorCaptor.capture());
    verify(responseObserver, never()).onNext(any());
    verify(responseObserver, never()).onCompleted();
    assertThat(errorCaptor.getValue()).isInstanceOf(StatusRuntimeException.class);
    assertThat(Status.fromThrowable(errorCaptor.getValue()).getCode())
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
    verify(recommendationService, never()).recommend(any());
  }

  /**
   * Given valid {@code user_id} and {@code conversation_id}, when {@code chatWithAssistant} is
   * called, then the request is delegated to {@link ChatAssistantService} and the result is
   * translated into a {@link ChatResponse}.
   */
  @Test
  @DisplayName("chatWithAssistant delegates and translates the response")
  void chatWithAssistantDelegatesAndTranslates() {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    ChatRequest request =
        ChatRequest.newBuilder()
            .setUserId(userId.toString())
            .setConversationId(conversationId.toString())
            .setMessage("What should I watch tonight?")
            .build();
    when(chatAssistantService.chat(any()))
        .thenReturn(new ChatResponseDto(conversationId, "Try Inception."));
    @SuppressWarnings("unchecked")
    StreamObserver<ChatResponse> responseObserver = mock(StreamObserver.class);

    aiGrpcService.chatWithAssistant(request, responseObserver);

    ArgumentCaptor<ChatRequestDto> requestCaptor = ArgumentCaptor.forClass(ChatRequestDto.class);
    verify(chatAssistantService).chat(requestCaptor.capture());
    assertThat(requestCaptor.getValue().userId()).isEqualTo(userId);
    assertThat(requestCaptor.getValue().conversationId()).isEqualTo(conversationId);
    assertThat(requestCaptor.getValue().message()).isEqualTo("What should I watch tonight?");

    ArgumentCaptor<ChatResponse> responseCaptor = ArgumentCaptor.forClass(ChatResponse.class);
    verify(responseObserver).onNext(responseCaptor.capture());
    verify(responseObserver).onCompleted();
    verify(responseObserver, never()).onError(any());
    assertThat(responseCaptor.getValue().getConversationId()).isEqualTo(conversationId.toString());
    assertThat(responseCaptor.getValue().getReply()).isEqualTo("Try Inception.");
  }

  /**
   * Given an empty {@code conversation_id}, when {@code chatWithAssistant} is called, then it's
   * treated as "start a new conversation" ({@code null} conversation id), not a parse failure.
   */
  @Test
  @DisplayName("chatWithAssistant treats an empty conversation_id as a new conversation")
  void chatWithAssistantTreatsEmptyConversationIdAsNew() {
    UUID userId = UUID.randomUUID();
    ChatRequest request =
        ChatRequest.newBuilder().setUserId(userId.toString()).setMessage("Hi").build();
    when(chatAssistantService.chat(any()))
        .thenReturn(new ChatResponseDto(UUID.randomUUID(), "Hello!"));
    @SuppressWarnings("unchecked")
    StreamObserver<ChatResponse> responseObserver = mock(StreamObserver.class);

    aiGrpcService.chatWithAssistant(request, responseObserver);

    ArgumentCaptor<ChatRequestDto> requestCaptor = ArgumentCaptor.forClass(ChatRequestDto.class);
    verify(chatAssistantService).chat(requestCaptor.capture());
    assertThat(requestCaptor.getValue().conversationId()).isNull();
  }

  /**
   * Given a {@code user_id} and {@code conversation_id} where the latter isn't a valid UUID, when
   * {@code chatWithAssistant} is called, then the observer receives an {@code INVALID_ARGUMENT}
   * error instead of reaching {@link ChatAssistantService}.
   */
  @Test
  @DisplayName("chatWithAssistant rejects a non-UUID conversation_id without calling the service")
  void chatWithAssistantRejectsInvalidConversationId() {
    ChatRequest request =
        ChatRequest.newBuilder()
            .setUserId(UUID.randomUUID().toString())
            .setConversationId("not-a-uuid")
            .setMessage("Hi")
            .build();
    @SuppressWarnings("unchecked")
    StreamObserver<ChatResponse> responseObserver = mock(StreamObserver.class);

    aiGrpcService.chatWithAssistant(request, responseObserver);

    ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
    verify(responseObserver).onError(errorCaptor.capture());
    verify(responseObserver, never()).onNext(any());
    assertThat(Status.fromThrowable(errorCaptor.getValue()).getCode())
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
    verify(chatAssistantService, never()).chat(any());
  }
}
