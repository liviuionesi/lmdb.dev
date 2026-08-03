package com.filmpire.ai.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmpire.ai.config.AiTestConfig;
import com.filmpire.ai.repository.ConversationRepository;
import com.filmpire.ai.service.SpeechToTextService;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integration tests for ai-service: full controller → service → PostgreSQL (pgvector) →
 * (WireMock-simulated) movie-service stack.
 *
 * <p>{@link ChatModel} and {@link EmbeddingModel} are Mockito mocks ({@link AiTestConfig}) standing
 * in for Ollama, which isn't reachable in CI — everything else (Flyway migration, JPA mapping, the
 * ANN query, the REST contract) runs against real infrastructure via Testcontainers (a {@code
 * pgvector/pgvector} PostgreSQL container) and WireMock stands in for movie-service.
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

  /**
   * testcontainers-postgresql 2.x's PostgreSQLContainer natively recognizes pgvector/pgvector as
   * compatible.
   */
  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ConversationRepository conversationRepository;

  @Autowired private ChatModel chatModel;

  @Autowired private EmbeddingModel embeddingModel;

  @Autowired private SpeechToTextService speechToTextService;

  /** Points MovieCatalogClient at WireMock instead of {@code lb://movie-service}. */
  @DynamicPropertySource
  static void movieServiceProperties(DynamicPropertyRegistry registry) {
    registry.add("movie-service.base-url", () -> "http://localhost:9992");
  }

  /**
   * No leftover mock stubbing between tests. DB isolation comes from the class-level
   * {@code @Transactional} rollback instead of manual {@code deleteAll()} — it's what lets {@link
   * #chatStartsNewConversationAndPersistsBothTurns} and friends read the LAZY {@code messages}
   * collection after the MockMvc call returns.
   */
  @BeforeEach
  void cleanSlate() {
    reset(chatModel);
    reset(embeddingModel);
    reset(speechToTextService);
    // ChatClient's internals call chatModel.getOptions().mutate() unconditionally
    // (DefaultChatClientUtils) — an unstubbed mock returns null there and NPEs
    // before the prompt is ever built, regardless of what call() is stubbed to do.
    when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    when(embeddingModel.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(zeroVector());
  }

  /**
   * Sends a chat request with no {@code conversationId} and verifies a new {@link
   * com.filmpire.ai.model.Conversation} is created holding both the user's message and the
   * assistant's reply — the persistence half of the chat contract, not just the HTTP response
   * shape.
   *
   * @throws Exception if the MockMvc request fails to execute
   */
  @Test
  @DisplayName(
      "POST /api/v1/ai/chat with no conversationId starts a new conversation and persists both turns")
  void chatStartsNewConversationAndPersistsBothTurns() throws Exception {
    stubAssistantReply("Fight Club is a great pick given your taste.");
    UUID userId = UUID.randomUUID();

    String body =
        objectMapper.writeValueAsString(
            Map.of("userId", userId, "message", "Recommend me something like Se7en"));

    mockMvc
        .perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reply").value("Fight Club is a great pick given your taste."))
        .andExpect(jsonPath("$.conversationId").exists());

    assertThat(conversationRepository.findAll()).hasSize(1);
    assertThat(conversationRepository.findAll().get(0).getMessages()).hasSize(2);
  }

  /**
   * Sends a second chat request carrying the {@code conversationId} from the first, and verifies
   * the two turns land in the same conversation (one row, four messages) rather than each request
   * creating its own — proving {@code conversationId} really is a continuation key, not just an
   * echoed field.
   *
   * @throws Exception if the MockMvc request fails to execute
   */
  @Test
  @DisplayName(
      "POST /api/v1/ai/chat with an existing conversationId appends to it instead of starting a new one")
  void chatContinuesExistingConversation() throws Exception {
    stubAssistantReply("Sure — here's a follow-up answer.");
    UUID userId = UUID.randomUUID();

    String firstBody = objectMapper.writeValueAsString(Map.of("userId", userId, "message", "hi"));
    String firstResponse =
        mockMvc
            .perform(
                post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content(firstBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String conversationId = objectMapper.readTree(firstResponse).get("conversationId").asText();

    String secondBody =
        objectMapper.writeValueAsString(
            Map.of(
                "userId",
                userId,
                "conversationId",
                conversationId,
                "message",
                "and another thing"));
    mockMvc
        .perform(
            post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content(secondBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conversationId").value(conversationId));

    assertThat(conversationRepository.findAll()).hasSize(1);
    assertThat(conversationRepository.findAll().get(0).getMessages()).hasSize(4);
  }

  /**
   * Creates a conversation as one user, then attempts to continue it as a different user, and
   * verifies the request is rejected with 404 — the ownership check in {@link
   * com.filmpire.ai.repository.ConversationRepository#findByIdAndUserId} must reject the mismatch
   * rather than the conversation being globally addressable by id.
   *
   * @throws Exception if the MockMvc request fails to execute
   */
  @Test
  @DisplayName(
      "POST /api/v1/ai/chat with another user's conversationId returns 404, not someone else's history")
  void chatWithAnotherUsersConversationReturns404() throws Exception {
    stubAssistantReply("irrelevant");
    UUID owner = UUID.randomUUID();
    UUID intruder = UUID.randomUUID();

    String ownerBody =
        objectMapper.writeValueAsString(Map.of("userId", owner, "message", "private"));
    String ownerResponse =
        mockMvc
            .perform(
                post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content(ownerBody))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String conversationId = objectMapper.readTree(ownerResponse).get("conversationId").asText();

    String intruderBody =
        objectMapper.writeValueAsString(
            Map.of("userId", intruder, "conversationId", conversationId, "message", "let me in"));
    mockMvc
        .perform(
            post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content(intruderBody))
        .andExpect(status().isNotFound());
  }

  /**
   * Stubs movie-service (via WireMock) to return one candidate movie and stubs the chat model to
   * recommend exactly that candidate, then verifies the recommendation response echoes the
   * candidate's real {@code movieId} and score — confirming recommendations flow through the
   * WireMock-simulated catalog rather than being fabricated locally.
   *
   * @throws Exception if the MockMvc request fails to execute
   */
  @Test
  @DisplayName(
      "POST /api/v1/ai/recommendations ranks candidates fetched from movie-service, never invented ones")
  void recommendReturnsRankedRecommendationsFromCatalog() throws Exception {
    stubFor(
        WireMock.get(urlPathEqualTo("/api/v1/movies/popular"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                {"content":[{"tmdbId":550,"title":"Fight Club","overview":"...","voteAverage":8.4}],
                 "pageNumber":0,"pageSize":30,"totalElements":1,"totalPages":1,
                 "first":true,"last":true,"hasNext":false,"hasPrevious":false,"numberOfElements":1}
                """)));
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(
            chatResponse(
                """
            [{"movieId":"550","score":0.95,"reason":"Same director's dark tone as Se7en"}]
            """));

    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "userId", UUID.randomUUID(),
                "recentMovies", List.of("Se7en"),
                "count", 1));

    mockMvc
        .perform(
            post("/api/v1/ai/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recommendations[0].movieId").value("550"))
        .andExpect(jsonPath("$.recommendations[0].score").value(0.95));
  }

  /**
   * Stubs movie-service to return zero candidates and verifies the response is a 200 with an empty
   * recommendations list, not an error — {@link
   * com.filmpire.ai.service.RecommendationService#recommend} must short-circuit before ever calling
   * the chat model when there's nothing to rank.
   *
   * @throws Exception if the MockMvc request fails to execute
   */
  @Test
  @DisplayName(
      "POST /api/v1/ai/recommendations returns an empty list rather than failing when movie-service has nothing to offer")
  void recommendWithNoCandidatesReturnsEmptyList() throws Exception {
    stubFor(
        WireMock.get(urlPathEqualTo("/api/v1/movies/popular"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                {"content":[],"pageNumber":0,"pageSize":30,"totalElements":0,"totalPages":0,
                 "first":true,"last":true,"hasNext":false,"hasPrevious":false,"numberOfElements":0}
                """)));

    String body =
        objectMapper.writeValueAsString(
            Map.of("userId", UUID.randomUUID(), "recentMovies", List.of("Anything"), "count", 5));

    mockMvc
        .perform(
            post("/api/v1/ai/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recommendations").isEmpty());
  }

  /**
   * Creates three taste profiles via the recommendations endpoint (an action fan, a romance fan,
   * and a caller who is also an action fan), then searches for "explosions" with an embedding close
   * to the action profile. Verifies the action fan ranks first, the romance fan second, and the
   * caller itself never appears — the {@code excludeUserId} clause in {@link
   * com.filmpire.ai.repository.UserTasteProfileRepository#findNearestNeighbours} must exclude the
   * caller's own row even though it's the closest match.
   *
   * @throws Exception if the MockMvc request fails to execute
   */
  @Test
  @DisplayName(
      "GET /api/v1/ai/search/semantic returns the nearest taste profile first, excludes the caller")
  void semanticSearchReturnsNearestNeighbourExcludingSelf() throws Exception {
    stubFor(
        WireMock.get(urlPathEqualTo("/api/v1/movies/popular"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
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

    mockMvc
        .perform(
            get("/api/v1/ai/search/semantic")
                .param("query", "explosions")
                .param("userId", caller.toString())
                .param("k", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
        .andExpect(jsonPath("$[0].userId").value(actionFan.toString()))
        .andExpect(jsonPath("$[1].userId").value(romanceFan.toString()));
  }

  /**
   * Given an audio upload, when the transcription succeeds, then the endpoint returns the
   * recognized text — verifies the multipart request contract and response shape. {@link
   * SpeechToTextService} itself is a Mockito mock ({@link com.filmpire.ai.config.AiTestConfig})
   * since no Vosk model is downloaded in CI; real audio-handling behaviour is covered by {@link
   * com.filmpire.ai.service.SpeechToTextServiceTest}.
   */
  @Test
  @DisplayName("POST /api/v1/ai/speech-to-text returns the transcribed text")
  void speechToTextReturnsTranscribedText() throws Exception {
    when(speechToTextService.transcribe(any())).thenReturn("show me action movies");

    MockMultipartFile audio =
        new MockMultipartFile("audio", "command.wav", "audio/wav", "fake-wav-bytes".getBytes());

    mockMvc
        .perform(multipart("/api/v1/ai/speech-to-text").file(audio))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("show me action movies"));
  }

  /**
   * Given the recognizer understood nothing, when speech-to-text is called, then the endpoint still
   * returns 200 with empty text rather than treating silence/noise as an error — matches {@link
   * SpeechToTextService}'s own empty-string-not-null contract.
   */
  @Test
  @DisplayName("POST /api/v1/ai/speech-to-text returns empty text when nothing was recognized")
  void speechToTextReturnsEmptyTextWhenNothingRecognized() throws Exception {
    when(speechToTextService.transcribe(any())).thenReturn("");

    MockMultipartFile silence =
        new MockMultipartFile(
            "audio", "silence.wav", "audio/wav", "fake-silent-wav-bytes".getBytes());

    mockMvc
        .perform(multipart("/api/v1/ai/speech-to-text").file(silence))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value(""));
  }

  /**
   * Creates a taste profile as a side effect of a recommendations request — {@link
   * com.filmpire.ai.service.RecommendationService} is the only writer of {@link
   * com.filmpire.ai.model.UserTasteProfile}, so tests that need a profile to exist go through this
   * endpoint rather than inserting one directly.
   *
   * @param userId the user to create a profile for
   * @param recentMoviesText the text embedded to produce that user's taste vector
   * @throws Exception if the MockMvc request fails to execute
   */
  private void createTasteProfile(UUID userId, String recentMoviesText) throws Exception {
    String body =
        objectMapper.writeValueAsString(
            Map.of("userId", userId, "recentMovies", List.of(recentMoviesText), "count", 1));
    mockMvc
        .perform(
            post("/api/v1/ai/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  /**
   * Stubs the mocked {@link ChatModel} to return a fixed reply for any prompt.
   *
   * @param reply the assistant reply the next chat call should produce
   */
  private void stubAssistantReply(String reply) {
    when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(reply));
  }

  /**
   * Wraps plain text in the {@link ChatResponse}/{@link Generation} shape Spring AI's {@link
   * ChatModel} contract requires.
   *
   * @param text the assistant reply text
   * @return a single-generation chat response containing {@code text}
   */
  private static ChatResponse chatResponse(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  /**
   * @return a 768-dimension all-zero embedding, matching {@code nomic-embed-text}'s dimension
   */
  private static float[] zeroVector() {
    return new float[768];
  }

  /**
   * Builds a unit vector with a single 1.0 component, used so distinct taste profiles are trivially
   * distinguishable by cosine distance in assertions.
   *
   * @param index the dimension set to 1.0
   * @return a 768-dimension vector, all zero except {@code index}
   */
  private static float[] oneHot(int index) {
    float[] v = new float[768];
    v[index] = 1.0f;
    return v;
  }

  /**
   * Builds a vector mostly aligned with one dimension but nudged toward the next, so it's closer
   * (by cosine distance) to {@link #oneHot} at {@code index} than to any other one-hot vector,
   * without being identical to it.
   *
   * @param index the dominant dimension
   * @param secondary the weight given to {@code index + 1}; the dominant dimension gets {@code 1.0
   *     - secondary}
   * @return a 768-dimension vector split between {@code index} and {@code index + 1}
   */
  private static float[] mostlyOneHot(int index, float secondary) {
    float[] v = new float[768];
    v[index] = 1.0f - secondary;
    v[index + 1] = secondary;
    return v;
  }
}
