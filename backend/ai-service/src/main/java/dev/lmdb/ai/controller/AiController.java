package dev.lmdb.ai.controller;

import dev.lmdb.ai.dto.ChatRequestBodyDto;
import dev.lmdb.ai.dto.ChatRequestDto;
import dev.lmdb.ai.dto.ChatResponseDto;
import dev.lmdb.ai.dto.RecommendationRequestBodyDto;
import dev.lmdb.ai.dto.RecommendationRequestDto;
import dev.lmdb.ai.dto.RecommendationResponseDto;
import dev.lmdb.ai.dto.SimilarUserDto;
import dev.lmdb.ai.dto.TranscriptionResponseDto;
import dev.lmdb.ai.security.CallerIdentity;
import dev.lmdb.ai.service.ChatAssistantService;
import dev.lmdb.ai.service.RecommendationService;
import dev.lmdb.ai.service.SemanticSearchService;
import dev.lmdb.ai.service.SpeechToTextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST surface for ai-service's features: catalog-grounded recommendations, the conversational
 * assistant, semantic search over user taste profiles, and offline speech-to-text for the
 * frontend's voice control (#68). The recommendation and chat features are also exposed over gRPC
 * by {@link dev.lmdb.ai.grpc.AiGrpcService}.
 *
 * <p>Every user-scoped endpoint here takes the caller's identity from the gateway-issued {@code
 * X-User-Id} header via {@link CallerIdentity} — never from the request body or a query parameter,
 * which the caller controls. Speech-to-text is the one exception: it is not user-scoped and is
 * {@code permitAll} at the gateway, so it reads no identity at all.
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "AI API",
    description = "Recommendations, chat assistant, semantic search, and speech-to-text")
public class AiController {

  private final RecommendationService recommendationService;
  private final ChatAssistantService chatAssistantService;
  private final SemanticSearchService semanticSearchService;
  private final SpeechToTextService speechToTextService;

  /**
   * Generates ranked, explained movie recommendations from LMDB's own catalog for the authenticated
   * caller, and refreshes that caller's taste profile as a side effect.
   *
   * @param callerId the authenticated caller, from the gateway-issued {@code X-User-Id} header
   * @param body the caller's recent movies and how many recommendations to return
   * @return the ranked recommendation list
   */
  @PostMapping("/recommendations")
  @Operation(
      summary = "Get movie recommendations",
      description = "Recommendations computed from LMDB's own catalog, not TMDB")
  public ResponseEntity<RecommendationResponseDto> recommend(
      @RequestHeader(name = CallerIdentity.USER_ID_HEADER, required = false) String callerId,
      @Valid @RequestBody RecommendationRequestBodyDto body) {
    UUID userId = CallerIdentity.require(callerId);
    log.info("POST /api/v1/ai/recommendations - userId={}", userId);
    return ResponseEntity.ok(
        recommendationService.recommend(
            new RecommendationRequestDto(userId, body.recentMovies(), body.count())));
  }

  /**
   * Continues (or starts) a conversation with the chat assistant, as the authenticated caller.
   *
   * @param callerId the authenticated caller, from the gateway-issued {@code X-User-Id} header
   * @param body the caller's message, and optionally an existing conversation to continue
   * @return the assistant's reply and the conversation id
   */
  @PostMapping("/chat")
  @Operation(
      summary = "Chat with the assistant",
      description = "Persists the conversation; omit conversationId to start a new one")
  public ResponseEntity<ChatResponseDto> chat(
      @RequestHeader(name = CallerIdentity.USER_ID_HEADER, required = false) String callerId,
      @Valid @RequestBody ChatRequestBodyDto body) {
    UUID userId = CallerIdentity.require(callerId);
    log.info("POST /api/v1/ai/chat - userId={}", userId);
    return ResponseEntity.ok(
        chatAssistantService.chat(
            new ChatRequestDto(userId, body.conversationId(), body.message())));
  }

  /**
   * Finds users whose taste embedding is nearest to a free-text query.
   *
   * @param callerId the authenticated caller, from the gateway-issued {@code X-User-Id} header;
   *     excluded from its own results
   * @param query free-text description of a taste
   * @param k how many neighbours to return (default 5)
   * @return the nearest users, closest first
   */
  @GetMapping("/search/semantic")
  @Operation(
      summary = "Semantic search",
      description = "ANN search over user taste embeddings (pgvector)")
  public ResponseEntity<List<SimilarUserDto>> semanticSearch(
      @RequestHeader(name = CallerIdentity.USER_ID_HEADER, required = false) String callerId,
      @RequestParam String query,
      @RequestParam(defaultValue = "5") int k) {
    UUID userId = CallerIdentity.require(callerId);
    log.info("GET /api/v1/ai/search/semantic - userId={}, k={}", userId, k);
    return ResponseEntity.ok(semanticSearchService.findSimilarUsers(query, userId, k));
  }

  /**
   * Transcribes an uploaded voice-command audio clip via Vosk (#68). Not user-scoped — the clip is
   * transcribed and discarded, nothing is read or written per user.
   *
   * @param audio a WAV (PCM) recording of the spoken command
   * @return the recognized text
   */
  @PostMapping(value = "/speech-to-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Speech to text",
      description = "Offline transcription via a local Vosk model — no cloud STT provider")
  public ResponseEntity<TranscriptionResponseDto> speechToText(
      @RequestParam("audio") MultipartFile audio) {
    log.info("POST /api/v1/ai/speech-to-text - {} bytes", audio.getSize());
    return ResponseEntity.ok(new TranscriptionResponseDto(speechToTextService.transcribe(audio)));
  }
}
