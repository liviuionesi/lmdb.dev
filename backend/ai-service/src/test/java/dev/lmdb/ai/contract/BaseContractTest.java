package dev.lmdb.ai.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.lmdb.ai.controller.AiController;
import dev.lmdb.ai.dto.MovieRecommendationDto;
import dev.lmdb.ai.dto.RecommendationRequestDto;
import dev.lmdb.ai.dto.RecommendationResponseDto;
import dev.lmdb.ai.service.ChatAssistantService;
import dev.lmdb.ai.service.QueryAggregationService;
import dev.lmdb.ai.service.QueryParsingService;
import dev.lmdb.ai.service.RecommendationService;
import dev.lmdb.ai.service.SemanticSearchService;
import dev.lmdb.ai.service.SpeechToTextService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base setup class for Spring Cloud Contract generated producer tests in {@code ai-service}. Mocks
 * {@link RecommendationService} and other AI services and configures {@link RestAssuredMockMvc}.
 */
public abstract class BaseContractTest {

  /**
   * Sets up mock MVC controller environment with stubbed AI recommendations before each contract
   * test execution.
   */
  @BeforeEach
  public void setup() {
    RecommendationService recommendationService = mock(RecommendationService.class);
    ChatAssistantService chatAssistantService = mock(ChatAssistantService.class);
    SemanticSearchService semanticSearchService = mock(SemanticSearchService.class);
    SpeechToTextService speechToTextService = mock(SpeechToTextService.class);
    QueryParsingService queryParsingService = mock(QueryParsingService.class);
    QueryAggregationService queryAggregationService = mock(QueryAggregationService.class);

    MovieRecommendationDto recommendation =
        new MovieRecommendationDto(
            "550", 0.95, "Based on your preference for psychological thrillers.");

    RecommendationResponseDto responseDto = new RecommendationResponseDto(List.of(recommendation));

    when(recommendationService.recommend(any(RecommendationRequestDto.class)))
        .thenReturn(responseDto);

    AiController aiController =
        new AiController(
            recommendationService,
            chatAssistantService,
            semanticSearchService,
            speechToTextService,
            queryParsingService,
            queryAggregationService);

    RestAssuredMockMvc.standaloneSetup(aiController);
  }
}
