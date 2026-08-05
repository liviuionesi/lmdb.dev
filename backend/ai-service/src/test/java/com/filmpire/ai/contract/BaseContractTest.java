package com.filmpire.ai.contract;

import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mockito;

public abstract class BaseContractTest {

  @BeforeEach
  public void setup() {
    RecommendationService recommendationService = Mockito.mock(RecommendationService.class);
    ChatAssistantService chatAssistantService = Mockito.mock(ChatAssistantService.class);
    SemanticSearchService semanticSearchService = Mockito.mock(SemanticSearchService.class);
    SpeechToTextService speechToTextService = Mockito.mock(SpeechToTextService.class);

    MovieRecommendationDto recommendation =
        new MovieRecommendationDto(
            "550", 0.95, "Based on your preference for psychological thrillers.");

    RecommendationResponseDto responseDto =
        new RecommendationResponseDto(List.of(recommendation));

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
