package dev.lmdb.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.lmdb.ai.dto.QueryFilterRole;
import dev.lmdb.ai.dto.SearchSort;
import dev.lmdb.ai.dto.StructuredQueryFilterDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
   * A franchise ("James Bond") is not a person. The model must be able to name it in its own field,
   * and the years in the same reply must survive.
   */
  @Test
  @DisplayName("reads a franchise and keeps the years")
  void readsAFranchise() {
    stubReplies(
        """
        {"personName":null,"role":null,"yearFrom":2000,"yearTo":null,"collaborators":[],
         "genre":null,"negated":[],"franchise":"James Bond","keywords":[],
         "plainTitle":"list me the movies from the James Bond franchise after year 2000"}
        """);

    StructuredQueryFilterDto filter =
        service.parse("list me the movies from the James Bond franchise after year 2000");

    assertThat(filter.franchise()).isEqualTo("James Bond");
    assertThat(filter.yearFrom()).isEqualTo(2000);
    assertThat(filter.plainTitle()).isNull();
  }

  /** Keywords alone are structure: an echoed {@code plainTitle} next to them must be dropped. */
  @Test
  @DisplayName("treats keywords as structure, so an echoed plainTitle is dropped")
  void keywordsCountAsStructure() {
    stubReplies(
        """
        {"personName":null,"role":null,"yearFrom":null,"yearTo":null,"collaborators":[],
         "genre":null,"negated":[],"franchise":null,"keywords":["heist"],
         "plainTitle":"heist movies"}
        """);

    StructuredQueryFilterDto filter = service.parse("heist movies");

    assertThat(filter.keywords()).containsExactly("heist");
    assertThat(filter.plainTitle()).isNull();
  }

  /** Blank entries and the text "null" in the keyword list are noise, not keywords. */
  @Test
  @DisplayName("drops blank and \"null\" keywords and trims the rest")
  void cleansTheKeywordList() {
    stubReplies(
        """
        {"personName":"Brad Pitt","role":"ACTED","yearFrom":null,"yearTo":null,
         "collaborators":[],"genre":null,"negated":[],"franchise":"null",
         "keywords":["", "null", "  heist  ", "heist"],"plainTitle":null}
        """);

    StructuredQueryFilterDto filter = service.parse("Brad Pitt heist movies");

    assertThat(filter.keywords()).containsExactly("heist");
    assertThat(filter.franchise()).isNull();
  }

  /**
   * llama3.2 invented a person, a role and collaborators for a franchise query, and named no
   * franchise. Nothing usable is left after the query-text check except the year, so the parse
   * keeps only what the user said.
   */
  @Test
  @DisplayName("removes people and roles the model invented")
  void removesInventedValues() {
    stubReplies(
        """
        {"personName":"Daniel Craig","role":"ACTED","yearFrom":2000,"yearTo":2015,
         "collaborators":["Olga Kurylenko","Jesper Christensen"],"genre":null,"negated":[],
         "franchise":null,"keywords":[],"plainTitle":null}
        """);

    StructuredQueryFilterDto filter =
        service.parse("list me the movies from the James Bond franchise after year 2000");

    assertThat(filter.personName()).isNull();
    assertThat(filter.role()).isNull();
    assertThat(filter.collaborators()).isEmpty();
    assertThat(filter.yearFrom()).isEqualTo(2000);
    assertThat(filter.yearTo()).isNull();
  }

  /**
   * A reply that is valid JSON but says nothing the query supports is as useless as broken JSON, so
   * it gets the same retry.
   */
  @Test
  @DisplayName("retries when nothing in the reply is supported by the query")
  void retriesWhenNothingIsSupported() {
    stubReplies(
        """
        {"personName":"Daniel Craig","role":"ACTED","yearFrom":null,"yearTo":null,
         "collaborators":[],"genre":null,"negated":[],"franchise":null,"keywords":[],
         "plainTitle":null}
        """,
        """
        {"personName":null,"role":null,"yearFrom":2000,"yearTo":null,"collaborators":[],
         "genre":null,"negated":[],"franchise":"James Bond","keywords":[],"plainTitle":null}
        """);

    StructuredQueryFilterDto filter = service.parse("James Bond movies after 2000");

    assertThat(filter.franchise()).isEqualTo("James Bond");
    verify(chatModel, times(2)).call(any(Prompt.class));
  }

  /** Reading a query must not vary from run to run, so the model is asked with no randomness. */
  @Test
  @DisplayName("asks the model with temperature 0")
  void asksWithNoRandomness() {
    stubReplies(
        """
        {"personName":"Tom Hanks","role":"ACTED","yearFrom":null,"yearTo":null,
         "collaborators":[],"genre":null,"negated":[],"franchise":null,"keywords":[],
         "plainTitle":null}
        """);

    service.parse("Tom Hanks movies");

    ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel).call(prompt.capture());
    assertThat(prompt.getValue().getOptions().getTemperature()).isEqualTo(0.0);
  }

  /**
   * The user's own example: "Tom Cruise movies from the last 20 years sorted by rating". The model
   * gives the person, and code reads the years and the sort from the text, counted from a fixed
   * date so the answer does not change with the calendar.
   */
  @Test
  @DisplayName("reads 'the last 20 years' and 'sorted by rating' from the text")
  void readsRelativeYearsAndSortFromTheText() {
    Clock fixed = Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneOffset.UTC);
    QueryParsingService dated = new QueryParsingService(ChatClient.builder(chatModel), fixed);
    stubReplies(
        """
        {"personName":"Tom Cruise","role":"ACTED","yearFrom":null,"yearTo":null,
         "collaborators":[],"genre":null,"negated":[],"franchise":null,"keywords":[],
         "plainTitle":null}
        """);

    StructuredQueryFilterDto filter =
        dated.parse("Tom Cruise movies from the last 20 years sorted by rating");

    assertThat(filter.personName()).isEqualTo("Tom Cruise");
    assertThat(filter.yearFrom()).isEqualTo(2006);
    assertThat(filter.sortBy()).isEqualTo(SearchSort.RATING);
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
