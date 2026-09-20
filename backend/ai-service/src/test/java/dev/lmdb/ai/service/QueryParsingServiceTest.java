package dev.lmdb.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.lmdb.ai.dto.QueryFilterRole;
import dev.lmdb.ai.dto.StructuredQueryFilterDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Tests {@link QueryParsingService} against raw model replies. The replies are copied from what
 * llama3.2 actually returned, mistakes included, so the whole path is exercised: reply text, JSON
 * conversion, then the cleanup of the filter. {@link ChatModel} is a Mockito mock; the {@link
 * ChatClient} on top of it is real.
 */
@DisplayName("QueryParsingService (model replies with the mistakes llama3.2 makes)")
class QueryParsingServiceTest {

  private ChatModel chatModel;
  private QueryParsingService service;

  /** Builds the service on a mocked model that returns whatever each test stubs. */
  @BeforeEach
  void setUp() {
    chatModel = mock(ChatModel.class);
    when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    service = new QueryParsingService(ChatClient.builder(chatModel));
  }

  /**
   * The model found a person and years but also copied the whole query into {@code plainTitle}. The
   * person and years must win: {@code plainTitle} means "no structure was found", and here
   * structure was found. Before this was handled, every such query was searched as a movie title
   * and returned nothing.
   */
  @Test
  @DisplayName("keeps the person and years when the model also echoes the query as plainTitle")
  void structureWinsOverAnEchoedPlainTitle() {
    stubReplies(
        """
        {"personName":"Tom Hanks","role":"ACTED","yearFrom":1990,"yearTo":1999,
         "collaborators":[],"genre":null,"negated":[],
         "plainTitle":"Tom Hanks movies in the 1990s"}
        """);

    StructuredQueryFilterDto filter = service.parse("Tom Hanks movies in the 1990s");

    assertThat(filter.personName()).isEqualTo("Tom Hanks");
    assertThat(filter.role()).isEqualTo(QueryFilterRole.ACTED);
    assertThat(filter.yearFrom()).isEqualTo(1990);
    assertThat(filter.yearTo()).isEqualTo(1999);
    assertThat(filter.plainTitle()).isNull();
  }

  /**
   * A query that is only a title has no structure, so its {@code plainTitle} must survive. This
   * guards the fix above from clearing the title in every case.
   */
  @Test
  @DisplayName("keeps plainTitle when the query names no person, year, genre or collaborator")
  void plainTitleSurvivesWhenThereIsNoStructure() {
    stubReplies(
        """
        {"personName":null,"role":null,"yearFrom":null,"yearTo":null,
         "collaborators":[],"genre":null,"negated":[],"plainTitle":"Inception"}
        """);

    StructuredQueryFilterDto filter = service.parse("Inception");

    assertThat(filter.plainTitle()).isEqualTo("Inception");
    assertThat(filter.personName()).isNull();
  }

  /**
   * The model wrote the text {@code "null"} where the schema wants a real null. Converting that to
   * {@link QueryFilterRole} used to throw, which dropped the whole query to the plain-title
   * fallback. The person and years in the same reply must still be used.
   */
  @Test
  @DisplayName("reads the text \"null\" as no role instead of failing the whole reply")
  void roleWrittenAsTheTextNullIsTreatedAsNoRole() {
    stubReplies(
        """
        {"personName":"Denzel Washington","role":"null","yearFrom":2010,"yearTo":2020,
         "collaborators":[],"genre":null,"negated":[],"plainTitle":null}
        """);

    StructuredQueryFilterDto filter = service.parse("Denzel Washington from 2010 to 2020");

    assertThat(filter.personName()).isEqualTo("Denzel Washington");
    assertThat(filter.role()).isNull();
    assertThat(filter.yearFrom()).isEqualTo(2010);
  }

  /** The prompt asks for upper case, but a lower-case role is still the role the user meant. */
  @Test
  @DisplayName("accepts a lower-case role")
  void lowerCaseRoleIsAccepted() {
    stubReplies(
        """
        {"personName":"Christopher Nolan","role":"directed","yearFrom":null,"yearTo":null,
         "collaborators":[],"genre":null,"negated":[],"plainTitle":null}
        """);

    StructuredQueryFilterDto filter = service.parse("movies directed by Christopher Nolan");

    assertThat(filter.role()).isEqualTo(QueryFilterRole.DIRECTED);
  }

  /**
   * For "movies directed by Christopher Nolan" the model returned {@code "genre": "Movie"}. No
   * movie has the genre "Movie", so keeping it would filter every result out.
   */
  @Test
  @DisplayName("drops a genre that just says movie or film")
  void genreThatOnlyMeansMovieIsDropped() {
    stubReplies(
        """
        {"personName":"Christopher Nolan","role":"DIRECTED","yearFrom":null,"yearTo":null,
         "collaborators":[],"genre":"Movie","negated":[],"plainTitle":null}
        """);

    StructuredQueryFilterDto filter = service.parse("movies directed by Christopher Nolan");

    assertThat(filter.genre()).isNull();
    assertThat(filter.personName()).isEqualTo("Christopher Nolan");
  }

  /** A blank name is not a person: with no structure left, the query must not be lost. */
  @Test
  @DisplayName("treats blank text fields as missing")
  void blankTextFieldsAreTreatedAsMissing() {
    stubReplies(
        """
        {"personName":"  ","role":null,"yearFrom":null,"yearTo":null,
         "collaborators":[],"genre":"","negated":[],"plainTitle":"Heat"}
        """);

    StructuredQueryFilterDto filter = service.parse("Heat");

    assertThat(filter.personName()).isNull();
    assertThat(filter.genre()).isNull();
    assertThat(filter.plainTitle()).isEqualTo("Heat");
  }

  /**
   * llama3.2 sometimes cuts its JSON off. One retry usually gets a whole answer, so the first
   * broken reply must not decide the result.
   */
  @Test
  @DisplayName("retries once when the first reply is cut-off JSON")
  void retriesOnceAfterATruncatedReply() {
    stubReplies(
        "{\"personName\":\"Brad Pitt\",\"role\":\"ACTED\",\"yearFrom\":null",
        """
        {"personName":"Brad Pitt","role":"ACTED","yearFrom":null,"yearTo":null,
         "collaborators":[],"genre":null,"negated":[],"plainTitle":null}
        """);

    StructuredQueryFilterDto filter = service.parse("movies with Brad Pitt");

    assertThat(filter.personName()).isEqualTo("Brad Pitt");
    verify(chatModel, times(2)).call(any(Prompt.class));
  }

  /**
   * When both attempts fail, the query is searched as a plain title, the existing safe fallback.
   * The model is asked exactly twice, not in a loop.
   */
  @Test
  @DisplayName("falls back to a plain title after two broken replies")
  void fallsBackToPlainTitleWhenBothAttemptsFail() {
    stubReplies("{\"personName\":\"Brad", "not json at all");

    StructuredQueryFilterDto filter = service.parse("movies with Brad Pitt");

    assertThat(filter.plainTitle()).isEqualTo("movies with Brad Pitt");
    assertThat(filter.personName()).isNull();
    verify(chatModel, times(2)).call(any(Prompt.class));
  }

  /**
   * Makes the mocked model return the given raw texts in order. The last one repeats if the service
   * calls more often than there are replies.
   *
   * @param replies raw model output, one entry per call
   */
  private void stubReplies(String... replies) {
    ChatResponse[] responses = new ChatResponse[replies.length];
    for (int i = 0; i < replies.length; i++) {
      responses[i] = new ChatResponse(List.of(new Generation(new AssistantMessage(replies[i]))));
    }
    var stubbing = when(chatModel.call(any(Prompt.class))).thenReturn(responses[0]);
    for (int i = 1; i < responses.length; i++) {
      stubbing = stubbing.thenReturn(responses[i]);
    }
  }
}
