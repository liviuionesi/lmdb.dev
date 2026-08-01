package com.filmpire.ai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmpire.ai.config.AiTestConfig;
import com.filmpire.ai.repository.ConversationRepository;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for ai-service: full controller → service → PostgreSQL
 * (pgvector) → (WireMock-simulated) movie-service stack (#36).
 *
 * <p>{@link ChatModel} and {@link EmbeddingModel} are Mockito mocks
 * ({@link AiTestConfig}) standing in for Ollama, which isn't reachable in
 * CI — everything else (Flyway migration, JPA mapping, the ANN query, the
 * REST contract) runs against real infrastructure. A successful context
 * load here IS the "{@code ddl-auto: validate} passes against the Flyway
 * schema" acceptance criterion (ADR-012).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(AiTestConfig.class)
@WireMockTest(httpPort = 9992)
@Transactional // MockMvc runs in-thread, so this also lets tests read the LAZY messages
                // collection after a request completes, and doubles as per-test DB rollback.
@DisplayName("AI Service Integration Tests (PostgreSQL/pgvector + WireMock movie-service)")
class AiServiceIntegrationTest {

    /** testcontainers-postgresql 2.x's PostgreSQLContainer natively recognizes pgvector/pgvector as compatible. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private EmbeddingModel embeddingModel;

    /** Points MovieCatalogClient at WireMock instead of {@code lb://movie-service}. */
    @DynamicPropertySource
    static void movieServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("movie-service.base-url", () -> "http://localhost:9992");
    }

    /**
     * No leftover mock stubbing between tests. DB isolation comes from the
     * class-level {@code @Transactional} rollback instead of manual
     * {@code deleteAll()} — it's what lets {@link #chatStartsNewConversationAndPersistsBothTurns}
     * and friends read the LAZY {@code messages} collection after the
     * MockMvc call returns.
     */
    @BeforeEach
    void cleanSlate() {
        reset(chatModel, embeddingModel);
        // ChatClient's internals call chatModel.getOptions().mutate() unconditionally
        // (DefaultChatClientUtils) — an unstubbed mock returns null there and NPEs
        // before the prompt is ever built, regardless of what call() is stubbed to do.
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(embeddingModel.embed(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(zeroVector());
    }

    @Test
    @DisplayName("POST /api/v1/ai/chat with no conversationId starts a new conversation and persists both turns")
    void chatStartsNewConversationAndPersistsBothTurns() throws Exception {
        stubAssistantReply("Fight Club is a great pick given your taste.");
        UUID userId = UUID.randomUUID();

        String body = objectMapper.writeValueAsString(Map.of(
            "userId", userId,
            "message", "Recommend me something like Se7en"
        ));

        mockMvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").value("Fight Club is a great pick given your taste."))
            .andExpect(jsonPath("$.conversationId").exists());

        assertThat(conversationRepository.findAll()).hasSize(1);
        assertThat(conversationRepository.findAll().get(0).getMessages()).hasSize(2);
    }

    @Test
    @DisplayName("POST /api/v1/ai/chat with an existing conversationId appends to it instead of starting a new one")
    void chatContinuesExistingConversation() throws Exception {
        stubAssistantReply("Sure — here's a follow-up answer.");
        UUID userId = UUID.randomUUID();

        String firstBody = objectMapper.writeValueAsString(Map.of("userId", userId, "message", "hi"));
        String firstResponse = mockMvc.perform(post("/api/v1/ai/chat")
                .contentType(MediaType.APPLICATION_JSON).content(firstBody))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String conversationId = objectMapper.readTree(firstResponse).get("conversationId").asText();

        String secondBody = objectMapper.writeValueAsString(Map.of(
            "userId", userId, "conversationId", conversationId, "message", "and another thing"));
        mockMvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content(secondBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationId").value(conversationId));

        assertThat(conversationRepository.findAll()).hasSize(1);
        assertThat(conversationRepository.findAll().get(0).getMessages()).hasSize(4);
    }

    @Test
    @DisplayName("POST /api/v1/ai/chat with another user's conversationId returns 404, not someone else's history")
    void chatWithAnotherUsersConversationReturns404() throws Exception {
        stubAssistantReply("irrelevant");
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();

        String ownerBody = objectMapper.writeValueAsString(Map.of("userId", owner, "message", "private"));
        String ownerResponse = mockMvc.perform(post("/api/v1/ai/chat")
                .contentType(MediaType.APPLICATION_JSON).content(ownerBody))
            .andReturn().getResponse().getContentAsString();
        String conversationId = objectMapper.readTree(ownerResponse).get("conversationId").asText();

        String intruderBody = objectMapper.writeValueAsString(Map.of(
            "userId", intruder, "conversationId", conversationId, "message", "let me in"));
        mockMvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content(intruderBody))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/ai/recommendations ranks candidates fetched from movie-service, never invented ones")
    void recommendReturnsRankedRecommendationsFromCatalog() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/api/v1/movies/popular")).willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {"content":[{"tmdbId":550,"title":"Fight Club","overview":"...","voteAverage":8.4}],
                 "pageNumber":0,"pageSize":30,"totalElements":1,"totalPages":1,
                 "first":true,"last":true,"hasNext":false,"hasPrevious":false,"numberOfElements":1}
                """)));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(
            """
            [{"movieId":"550","score":0.95,"reason":"Same director's dark tone as Se7en"}]
            """));

        String body = objectMapper.writeValueAsString(Map.of(
            "userId", UUID.randomUUID(),
            "recentMovies", List.of("Se7en"),
            "count", 1
        ));

        mockMvc.perform(post("/api/v1/ai/recommendations").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recommendations[0].movieId").value("550"))
            .andExpect(jsonPath("$.recommendations[0].score").value(0.95));
    }

    @Test
    @DisplayName("POST /api/v1/ai/recommendations returns an empty list rather than failing when movie-service has nothing to offer")
    void recommendWithNoCandidatesReturnsEmptyList() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/api/v1/movies/popular")).willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {"content":[],"pageNumber":0,"pageSize":30,"totalElements":0,"totalPages":0,
                 "first":true,"last":true,"hasNext":false,"hasPrevious":false,"numberOfElements":0}
                """)));

        String body = objectMapper.writeValueAsString(Map.of(
            "userId", UUID.randomUUID(), "recentMovies", List.of("Anything"), "count", 5));

        mockMvc.perform(post("/api/v1/ai/recommendations").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recommendations").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/ai/search/semantic returns the nearest taste profile first, excludes the caller")
    void semanticSearchReturnsNearestNeighbourExcludingSelf() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/api/v1/movies/popular")).willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {"content":[],"pageNumber":0,"pageSize":30,"totalElements":0,"totalPages":0,
                 "first":true,"last":true,"hasNext":false,"hasPrevious":false,"numberOfElements":0}
                """)));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("[]"));

        UUID actionFan = UUID.randomUUID();
        UUID romanceFan = UUID.randomUUID();
        UUID caller = UUID.randomUUID();

        when(embeddingModel.embed("action")).thenReturn(oneHot(0));
        when(embeddingModel.embed("romance")).thenReturn(oneHot(1));
        when(embeddingModel.embed("explosions")).thenReturn(mostlyOneHot(0, 0.1f));

        createTasteProfile(actionFan, "action");
        createTasteProfile(romanceFan, "romance");
        createTasteProfile(caller, "action"); // excluded from its own results below

        mockMvc.perform(get("/api/v1/ai/search/semantic")
                .param("query", "explosions")
                .param("userId", caller.toString())
                .param("k", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$[0].userId").value(actionFan.toString()))
            .andExpect(jsonPath("$[1].userId").value(romanceFan.toString()));
    }

    private void createTasteProfile(UUID userId, String recentMoviesText) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "userId", userId, "recentMovies", List.of(recentMoviesText), "count", 1));
        mockMvc.perform(post("/api/v1/ai/recommendations").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
    }

    private void stubAssistantReply(String reply) {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(reply));
    }

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static float[] zeroVector() {
        return new float[768];
    }

    private static float[] oneHot(int index) {
        float[] v = new float[768];
        v[index] = 1.0f;
        return v;
    }

    private static float[] mostlyOneHot(int index, float secondary) {
        float[] v = new float[768];
        v[index] = 1.0f - secondary;
        v[index + 1] = secondary;
        return v;
    }
}
