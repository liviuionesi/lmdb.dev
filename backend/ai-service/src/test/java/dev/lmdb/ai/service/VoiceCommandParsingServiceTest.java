package dev.lmdb.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.lmdb.ai.dto.ThemeMode;
import dev.lmdb.ai.dto.VoiceCommandDto;
import dev.lmdb.ai.dto.VoiceCommandType;
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
 * Unit tests for {@link VoiceCommandParsingService}'s own normalization logic (#214, Story #200) —
 * a {@link ChatModel} mock stands in for Ollama here (no Spring context, no Testcontainers), the
 * same lightweight style {@link SpeechToTextServiceTest} uses for its own model-adjacent
 * collaborator. {@link dev.lmdb.ai.integration.AiServiceIntegrationTest} covers the HTTP contract
 * end to end; this class exists because three of {@code normalize()}'s branches — a {@code
 * CHANGE_MODE} reply missing its mode, a {@code CHOOSE_GENRE} reply missing its genre, and a {@code
 * SEARCH} reply missing its query — have no coverage anywhere else, flagged by an independent
 * test-quality review pass. Constructing the service directly with {@link ChatClient#builder}
 * against a mocked {@link ChatModel} lets these run without Docker, unlike the integration suite.
 */
@DisplayName("VoiceCommandParsingService (normalize()'s validation branches)")
class VoiceCommandParsingServiceTest {

  private ChatModel chatModel;
  private VoiceCommandParsingService service;

  /**
   * Builds the service against a mocked {@link ChatModel}, stubbing {@code getOptions()} the same
   * way {@link dev.lmdb.ai.integration.AiServiceIntegrationTest#cleanSlate} does — {@link
   * ChatClient}'s internals call it unconditionally while assembling a prompt, and an unstubbed
   * mock returns {@code null} there, NPEing before {@code call()} is ever reached.
   */
  @BeforeEach
  void setUp() {
    chatModel = mock(ChatModel.class);
    when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    service = new VoiceCommandParsingService(ChatClient.builder(chatModel));
  }

  /**
   * Given the model classifies {@code CHANGE_MODE} but omits {@code mode}, when parsed, then the
   * result degrades to "no match" rather than being returned with a hole in it — {@code
   * VoiceControl.jsx} has no rendering for {@code mode: undefined}, so a caller must never see this
   * shape.
   */
  @Test
  @DisplayName(
      "CHANGE_MODE with a null mode degrades to no-match rather than an incomplete command")
  void changeModeWithNullModeDegradesToNoMatch() {
    stubReply(
        """
        {"command":"CHANGE_MODE","mode":null,"genreOrCategory":null,"query":null}
        """);

    VoiceCommandDto result = service.parse("switch theme", List.of());

    assertThat(result).isEqualTo(new VoiceCommandDto(null, null, null, null));
  }

  /**
   * Given the model classifies {@code CHOOSE_GENRE} but leaves {@code genreOrCategory} blank, when
   * parsed, then the result degrades to "no match" — dispatching {@code chooseGenre} with nothing
   * to browse would be worse than surfacing "no matching command."
   */
  @Test
  @DisplayName("CHOOSE_GENRE with a blank genreOrCategory degrades to no-match")
  void chooseGenreWithBlankGenreDegradesToNoMatch() {
    stubReply(
        """
        {"command":"CHOOSE_GENRE","mode":null,"genreOrCategory":"   ","query":null}
        """);

    VoiceCommandDto result = service.parse("show me something", List.of("Action", "Comedy"));

    assertThat(result).isEqualTo(new VoiceCommandDto(null, null, null, null));
  }

  /**
   * Given the model classifies {@code SEARCH} but leaves {@code query} blank, when parsed, then the
   * result falls back to a search over the raw transcript rather than "no match" — distinct from
   * the other two branches above: {@code SEARCH} is the catch-all the old regex table always
   * resolved to, so it degrades to something still usable instead of an empty result.
   */
  @Test
  @DisplayName("SEARCH with a blank query falls back to searching the raw transcript")
  void searchWithBlankQueryFallsBackToRawTranscript() {
    stubReply(
        """
        {"command":"SEARCH","mode":null,"genreOrCategory":null,"query":""}
        """);

    VoiceCommandDto result = service.parse("movies about time travel", List.of());

    assertThat(result)
        .isEqualTo(
            new VoiceCommandDto(VoiceCommandType.SEARCH, null, null, "movies about time travel"));
  }

  /**
   * Given a normal, complete {@code CHANGE_MODE} reply, when parsed, then it passes through
   * unchanged — the positive-path counterpart to {@link #changeModeWithNullModeDegradesToNoMatch},
   * proving the null-mode case above is a real validation branch and not just always-degrading
   * logic.
   */
  @Test
  @DisplayName("CHANGE_MODE with a mode present passes through unchanged")
  void changeModeWithModePresentPassesThrough() {
    stubReply(
        """
        {"command":"CHANGE_MODE","mode":"DARK","genreOrCategory":null,"query":null}
        """);

    VoiceCommandDto result = service.parse("dark mode please", List.of());

    assertThat(result)
        .isEqualTo(new VoiceCommandDto(VoiceCommandType.CHANGE_MODE, ThemeMode.DARK, null, null));
  }

  /**
   * Given a caller-supplied genre list, when a transcript is parsed, then the model prompt actually
   * contains it — proving the {@code "Known genres: ..."} line {@link
   * VoiceCommandParsingService#parse} builds really reaches the model, not just that a response
   * carrying a genre is passed through.
   */
  @Test
  @DisplayName("includes the caller-supplied genre list in the prompt sent to the model")
  void includesGenreListInThePrompt() {
    stubReply(
        """
        {"command":null,"mode":null,"genreOrCategory":null,"query":null}
        """);

    service.parse("show me something", List.of("Action", "Comedy"));

    ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel).call(promptCaptor.capture());
    String sentText =
        promptCaptor.getValue().getInstructions().stream()
            .filter(UserMessage.class::isInstance)
            .findFirst()
            .orElseThrow()
            .getText();

    assertThat(sentText).contains("Known genres: Action, Comedy");
  }

  /**
   * Given the model call itself throws (a flaky/unreachable Ollama, not a malformed reply), when
   * parsed, then the result falls back to a search over the raw transcript — the same posture
   * {@link QueryParsingService#parse} takes toward a failed call, verified directly here rather
   * than only through the integration suite's unparseable-JSON case.
   */
  @Test
  @DisplayName("a failed model call falls back to searching the raw transcript")
  void failedModelCallFallsBackToRawTranscript() {
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("model unreachable"));

    VoiceCommandDto result = service.parse("light mode please", List.of());

    assertThat(result)
        .isEqualTo(new VoiceCommandDto(VoiceCommandType.SEARCH, null, null, "light mode please"));
  }

  /**
   * Stubs the mocked {@link ChatModel} to return a fixed reply for the next {@code call()}.
   *
   * @param reply the raw JSON text the model should "generate"
   */
  private void stubReply(String reply) {
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(reply)))));
  }
}
