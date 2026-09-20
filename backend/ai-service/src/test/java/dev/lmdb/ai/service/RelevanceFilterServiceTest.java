package dev.lmdb.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.lmdb.ai.dto.SearchResultMovieDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Tests {@link RelevanceFilterService}. The model is a Mockito mock that returns fixed replies, so
 * each test controls which movies the "model" says fit the query and checks what the service keeps.
 */
@DisplayName("RelevanceFilterService (asking the model which movies fit the query)")
class RelevanceFilterServiceTest {

  private ChatModel chatModel;
  private RelevanceFilterService service;

  /** Builds the service on a mocked model. */
  @BeforeEach
  void setUp() {
    chatModel = mock(ChatModel.class);
    when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    service = new RelevanceFilterService(ChatClient.builder(chatModel));
  }

  /**
   * The model answers with the numbers of the movies that fit. Those are kept, in their original
   * order, and the others are dropped.
   */
  @Test
  @DisplayName("keeps the movies the model numbers and drops the rest")
  void keepsOnlyTheNumberedMovies() {
    stubReply("{\"matches\":[1,3]}");

    List<SearchResultMovieDto> kept =
        service.filter(
            "heist movies",
            List.of(movie(1, "Ocean's Eleven"), movie(2, "Titanic"), movie(3, "Heat")));

    assertThat(titles(kept)).containsExactly("Ocean's Eleven", "Heat");
  }

  /** The model sees the query itself and each movie's title and year, numbered from 1. */
  @Test
  @DisplayName("sends the query and the numbered movies to the model")
  void sendsTheQueryAndTheNumberedMovies() {
    stubReply("{\"matches\":[1]}");

    service.filter("heist movies", List.of(movie(1, "Heat"), movie(2, "Titanic")));

    ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel).call(prompt.capture());
    String user =
        prompt.getValue().getInstructions().stream()
            .filter(UserMessage.class::isInstance)
            .findFirst()
            .orElseThrow()
            .getText();
    assertThat(user)
        .contains("heist movies")
        .contains("1. Heat (1995)")
        .contains("2. Titanic (1995)");
  }

  /**
   * The same movies must get the same verdict every time, so the model is asked with no randomness.
   */
  @Test
  @DisplayName("asks the model with temperature 0")
  void asksWithNoRandomness() {
    stubReply("{\"matches\":[1]}");

    service.filter("q", List.of(movie(1, "One")));

    ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel).call(prompt.capture());
    assertThat(prompt.getValue().getOptions().getTemperature()).isEqualTo(0.0);
  }

  /** A number outside the list is a model mistake and must not become a result or an exception. */
  @Test
  @DisplayName("ignores numbers that are not in the list")
  void ignoresOutOfRangeNumbers() {
    stubReply("{\"matches\":[2,99,0,-1]}");

    List<SearchResultMovieDto> kept =
        service.filter("q", List.of(movie(1, "One"), movie(2, "Two")));

    assertThat(titles(kept)).containsExactly("Two");
  }

  /**
   * If the model call fails, the movies are returned unchanged. The check is an extra filter, so
   * losing it must not lose the search.
   */
  @Test
  @DisplayName("returns every movie when the model call fails")
  void returnsEverythingWhenTheModelFails() {
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("ollama is down"));

    List<SearchResultMovieDto> kept =
        service.filter("q", List.of(movie(1, "One"), movie(2, "Two")));

    assertThat(titles(kept)).containsExactly("One", "Two");
  }

  /** A reply that is not the expected JSON is treated like a failed call. */
  @Test
  @DisplayName("returns every movie when the reply is not valid JSON")
  void returnsEverythingWhenTheReplyIsGarbage() {
    stubReply("Sure! Here are the movies that fit: Heat and Titanic");

    List<SearchResultMovieDto> kept =
        service.filter("q", List.of(movie(1, "One"), movie(2, "Two")));

    assertThat(titles(kept)).containsExactly("One", "Two");
  }

  /** With nothing to judge there is nothing to ask. */
  @Test
  @DisplayName("does not call the model for an empty list")
  void doesNotCallTheModelForAnEmptyList() {
    List<SearchResultMovieDto> kept = service.filter("q", List.of());

    assertThat(kept).isEmpty();
    verify(chatModel, never()).call(any(Prompt.class));
  }

  /**
   * A long list is judged in chunks of 40, so one prompt never grows past what a small model reads
   * well. 90 movies give three calls, and each chunk's numbers start again from 1.
   */
  @Test
  @DisplayName("judges a long list in chunks of 40")
  void judgesALongListInChunks() {
    stubReply("{\"matches\":[1]}");
    List<SearchResultMovieDto> candidates = new ArrayList<>();
    for (int i = 1; i <= 90; i++) {
      candidates.add(movie(i, "Movie " + i));
    }

    List<SearchResultMovieDto> kept = service.filter("q", candidates);

    verify(chatModel, times(3)).call(any(Prompt.class));
    assertThat(titles(kept)).containsExactly("Movie 1", "Movie 41", "Movie 81");
  }

  /**
   * Builds a movie released in 1995.
   *
   * @param id movie id
   * @param title the title
   * @return the result
   */
  private static SearchResultMovieDto movie(long id, String title) {
    return new SearchResultMovieDto(id, title, "An overview.", "1995-06-01", null, 7.0);
  }

  /**
   * Lists the titles of some results.
   *
   * @param movies the results
   * @return their titles, in order
   */
  private static List<String> titles(List<SearchResultMovieDto> movies) {
    return movies.stream().map(SearchResultMovieDto::title).toList();
  }

  /**
   * Makes the mocked model return the same raw text for every call.
   *
   * @param reply the raw model output
   */
  private void stubReply(String reply) {
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(reply)))));
  }
}
