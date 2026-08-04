package com.filmpire.ai.controller;

import com.filmpire.ai.dto.ChatRequestDto;
import com.filmpire.ai.dto.ChatResponseDto;
import com.filmpire.ai.dto.RecommendationRequestDto;
import com.filmpire.ai.dto.RecommendationResponseDto;
import com.filmpire.ai.dto.SimilarUserDto;
import com.filmpire.ai.dto.TranscriptionResponseDto;
import com.filmpire.ai.service.ChatAssistantService;
import com.filmpire.ai.service.RecommendationService;
import com.filmpire.ai.service.SemanticSearchService;
import com.filmpire.ai.service.SpeechToTextService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST surface for ai-service's features: catalog-grounded recommendations, the conversational
 * assistant, semantic search over user taste profiles, and offline speech-to-text for the
 * frontend's voice control (#68). The recommendation and chat features are also exposed over gRPC
 * by {@link com.filmpire.ai.grpc.AiGrpcService}.
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
   * Generates ranked, explained movie recommendations from Filmpire's own catalog.
   *
   * @param request the user, their recent movies, and how many recommendations to return
   * @return the ranked recommendation list
   */
  @PostMapping("/recommendations")
  @Operation(
      summary = "Get movie recommendations",
      description = "Recommendations computed from Filmpire's own catalog, not TMDB")
  public ResponseEntity<RecommendationResponseDto> recommend(
      @Valid @RequestBody RecommendationRequestDto request) {
    log.info("POST /api/v1/ai/recommendations - userId={}", request.userId());
    return ResponseEntity.ok(recommendationService.recommend(request));
  }

  /**
   * Continues (or starts) a conversation with the chat assistant.
   *
   * @param request the user's message, and optionally an existing conversation to continue
   * @return the assistant's reply and the conversation id
   */
  @PostMapping("/chat")
  @Operation(
      summary = "Chat with the assistant",
      description = "Persists the conversation; omit conversationId to start a new one")
  public ResponseEntity<ChatResponseDto> chat(@Valid @RequestBody ChatRequestDto request) {
    log.info("POST /api/v1/ai/chat - userId={}", request.userId());
    return ResponseEntity.ok(chatAssistantService.chat(request));
  }

  /**
   * Finds users whose taste embedding is nearest to a free-text query.
   *
   * @param query free-text description of a taste
   * @param userId the caller, excluded from its own results
   * @param k how many neighbours to return (default 5)
   * @return the nearest users, closest first
   */
  @GetMapping("/search/semantic")
  @Operation(
      summary = "Semantic search",
      description = "ANN search over user taste embeddings (pgvector)")
  public ResponseEntity<List<SimilarUserDto>> semanticSearch(
      @RequestParam String query,
      @RequestParam UUID userId,
      @RequestParam(defaultValue = "5") int k) {
    log.info("GET /api/v1/ai/search/semantic - userId={}, k={}", userId, k);
    return ResponseEntity.ok(semanticSearchService.findSimilarUsers(query, userId, k));
  }

  /**
   * Transcribes an uploaded voice-command audio clip via Vosk (#68).
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
