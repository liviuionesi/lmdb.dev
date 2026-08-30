package dev.lmdb.ai.controller;

import dev.lmdb.ai.dto.ChatRequestBodyDto;
import dev.lmdb.ai.dto.ChatRequestDto;
import dev.lmdb.ai.dto.ChatResponseDto;
import dev.lmdb.ai.dto.NaturalLanguageSearchResponseDto;
import dev.lmdb.ai.dto.QueryParseRequestDto;
import dev.lmdb.ai.dto.RecommendationRequestBodyDto;
import dev.lmdb.ai.dto.RecommendationRequestDto;
import dev.lmdb.ai.dto.RecommendationResponseDto;
import dev.lmdb.ai.dto.SimilarUserDto;
import dev.lmdb.ai.dto.StructuredQueryFilterDto;
import dev.lmdb.ai.dto.TranscriptionResponseDto;
import dev.lmdb.ai.security.CallerIdentity;
import dev.lmdb.ai.service.ChatAssistantService;
import dev.lmdb.ai.service.QueryAggregationService;
import dev.lmdb.ai.service.QueryParsingService;
import dev.lmdb.ai.service.RecommendationService;
import dev.lmdb.ai.service.SemanticSearchService;
import dev.lmdb.ai.service.SpeechToTextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
 * assistant, semantic search over user taste profiles, natural-language search-query parsing (#202)
 * and execution (#203), and offline speech-to-text for the frontend's voice control (#68). The
 * recommendation and chat features are also exposed over gRPC by {@link
 * dev.lmdb.ai.grpc.AiGrpcService}; the search-query endpoints are deliberately REST-only (ADR-020 —
 * no backend-to-backend caller needs them today).
 *
 * <p>Every user-scoped endpoint here takes the caller's identity from the gateway-issued {@code
 * X-User-Id} header via {@link CallerIdentity} — never from the request body or a query parameter,
 * which the caller controls. Speech-to-text and the search-query endpoints are the exceptions: none
 * touch per-user data, so none read an identity at all (speech-to-text is additionally {@code
 * permitAll} at the gateway; the search-query endpoints still require a valid JWT via the gateway's
 * blanket {@code /api/v1/ai/**} rule, they just don't need to know who the caller is).
 */
@RestController
@RequestMapping("/api/v1/ai")
@Validated
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "AI API",
    description =
        "Recommendations, chat assistant, semantic search, natural-language search parsing and"
            + " execution, and speech-to-text")
public class AiController {

  private final RecommendationService recommendationService;
  private final ChatAssistantService chatAssistantService;
  private final SemanticSearchService semanticSearchService;
  private final SpeechToTextService speechToTextService;
  private final QueryParsingService queryParsingService;
  private final QueryAggregationService queryAggregationService;

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
      @RequestParam(defaultValue = "5") @Min(1) @Max(50) int k) {
    UUID userId = CallerIdentity.require(callerId);
    log.info("GET /api/v1/ai/search/semantic - userId={}, k={}", userId, k);
    return ResponseEntity.ok(semanticSearchService.findSimilarUsers(query, userId, k));
  }

  /**
   * Parses a free-text (typed or dictated) movie query into a structured filter — person, role,
   * year range, collaborators, genre, negation — or an explicit plain-title fallback when the query
   * carries no detectable structure (#202, ADR-020). Not user-scoped — parsing touches no per-user
   * data. Extraction only: this does not call actor-service/movie-service or execute the filter
   * (that's the cross-service aggregation endpoint, #203).
   *
   * @param body the raw query text
   * @return the extracted structured filter, or a plain-title fallback
   */
  @PostMapping("/search/query")
  @Operation(
      summary = "Parse a natural-language movie query",
      description =
          "Extracts a structured filter (person/role/year range/collaborators/genre/negation) or a"
              + " plain-title fallback; does not execute the filter")
  public ResponseEntity<StructuredQueryFilterDto> parseQuery(
      @Valid @RequestBody QueryParseRequestDto body) {
    log.info("POST /api/v1/ai/search/query");
    return ResponseEntity.ok(queryParsingService.parse(body.query()));
  }

  /**
   * Parses AND executes a free-text (typed or dictated) movie query end to end (#203, ADR-020):
   * extracts a structured filter or plain-title fallback ({@link QueryParsingService}, #202), then
   * resolves it against actor-service and movie-service, returning actual matching movies rather
   * than the filter itself. This is the endpoint the search bar submits to; {@link #parseQuery} (no
   * execution) is what powers live query highlighting as the user types (#199).
   *
   * @param body the raw query text
   * @return the matching movies
   */
  @PostMapping("/search/execute")
  @Operation(
      summary = "Execute a natural-language movie search",
      description =
          "Parses the query and resolves it against actor-service/movie-service, returning matching"
              + " movies")
  public ResponseEntity<NaturalLanguageSearchResponseDto> executeSearch(
      @Valid @RequestBody QueryParseRequestDto body) {
    log.info("POST /api/v1/ai/search/execute");
    return ResponseEntity.ok(queryAggregationService.search(body.query()));
  }

  /**
   * Transcribes an uploaded voice-command audio clip via Vosk (#68, bilingual per #212). Not
   * user-scoped — the clip is transcribed and discarded, nothing is read or written per user.
   *
   * @param audio a WAV (PCM) recording of the spoken command
   * @param language {@code en}/{@code de} to select the transcription model, or omitted to default
   *     to English (pre-#212 behavior)
   * @return the recognized text
   */
  @PostMapping(value = "/speech-to-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Speech to text",
      description = "Offline transcription via a local Vosk model — no cloud STT provider")
  public ResponseEntity<TranscriptionResponseDto> speechToText(
      @RequestParam("audio") MultipartFile audio,
      @RequestParam(value = "language", required = false) String language) {
    log.info("POST /api/v1/ai/speech-to-text - {} bytes, language={}", audio.getSize(), language);
    return ResponseEntity.ok(
        new TranscriptionResponseDto(speechToTextService.transcribe(audio, language)));
  }
}
