package com.filmpire.ai.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.filmpire.ai.controller.AiController;
import com.filmpire.ai.dto.MovieRecommendationDto;
import com.filmpire.ai.dto.RecommendationRequestDto;
import com.filmpire.ai.dto.RecommendationResponseDto;
import com.filmpire.ai.service.ChatAssistantService;
import com.filmpire.ai.service.RecommendationService;
import com.filmpire.ai.service.SemanticSearchService;
import com.filmpire.ai.service.SpeechToTextService;
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
            speechToTextService);

    RestAssuredMockMvc.standaloneSetup(aiController);
  }
}
