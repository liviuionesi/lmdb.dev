package com.filmpire.ai.controller;

import com.filmpire.ai.dto.ChatRequestDto;
import com.filmpire.ai.dto.ChatResponseDto;
import com.filmpire.ai.dto.RecommendationRequestDto;
import com.filmpire.ai.dto.RecommendationResponseDto;
import com.filmpire.ai.dto.SimilarUserDto;
import com.filmpire.ai.service.ChatAssistantService;
import com.filmpire.ai.service.RecommendationService;
import com.filmpire.ai.service.SemanticSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST surface for ai-service's three features (#36, ARCHITECTURE.md §3.7):
 * catalog-grounded recommendations, the conversational assistant, and
 * semantic search over user taste profiles. The same operations are also
 * exposed over gRPC by {@link com.filmpire.ai.grpc.AiGrpcService} for the
 * recommendation and chat features.
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI API", description = "Recommendations, chat assistant, and semantic search")
public class AiController {

    private final RecommendationService recommendationService;
    private final ChatAssistantService chatAssistantService;
    private final SemanticSearchService semanticSearchService;

    /**
     * Generates ranked, explained movie recommendations from Filmpire's own catalog.
     *
     * @param request the user, their recent movies, and how many recommendations to return
     * @return the ranked recommendation list
     */
    @PostMapping("/recommendations")
    @Operation(summary = "Get movie recommendations", description = "Recommendations computed from Filmpire's own catalog, not TMDB")
    public ResponseEntity<RecommendationResponseDto> recommend(@Valid @RequestBody RecommendationRequestDto request) {
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
    @Operation(summary = "Chat with the assistant", description = "Persists the conversation; omit conversationId to start a new one")
    public ResponseEntity<ChatResponseDto> chat(@Valid @RequestBody ChatRequestDto request) {
        log.info("POST /api/v1/ai/chat - userId={}", request.userId());
        return ResponseEntity.ok(chatAssistantService.chat(request));
    }

    /**
     * Finds users whose taste embedding is nearest to a free-text query.
     *
     * @param query  free-text description of a taste
     * @param userId the caller, excluded from its own results
     * @param k      how many neighbours to return (default 5)
     * @return the nearest users, closest first
     */
    @GetMapping("/search/semantic")
    @Operation(summary = "Semantic search", description = "ANN search over user taste embeddings (pgvector)")
    public ResponseEntity<List<SimilarUserDto>> semanticSearch(
        @RequestParam String query,
        @RequestParam UUID userId,
        @RequestParam(defaultValue = "5") int k
    ) {
        log.info("GET /api/v1/ai/search/semantic - userId={}, k={}", userId, k);
        return ResponseEntity.ok(semanticSearchService.findSimilarUsers(query, userId, k));
    }
}
