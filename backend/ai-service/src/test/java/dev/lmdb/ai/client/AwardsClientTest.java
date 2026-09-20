package dev.lmdb.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.lmdb.ai.dto.OscarCategory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests {@link AwardsClient}. A {@link MockRestServiceServer} plays Wikidata, so each test checks
 * the query the client sends, how it reads the reply, and how it caches and fails.
 */
@DisplayName("AwardsClient (Academy Award winners from Wikidata)")
class AwardsClientTest {

  private static final String BASE = "https://wikidata.test";

  private static final String TWO_WINNERS =
      """
      {"head":{"vars":["tmdb"]},
       "results":{"bindings":[
         {"tmdb":{"type":"literal","value":"872585"}},
         {"tmdb":{"type":"literal","value":"98"}},
         {"tmdb":{"type":"literal","value":"98"}}]}}
      """;

  private MockRestServiceServer server;
  private MutableClock clock;
  private AwardsClient client;

  /** A clock a test can move forward. */
  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-09-20T10:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  /** Builds a client whose HTTP calls go to the mock server, on a clock the test controls. */
  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    clock = new MutableClock();
    client = new AwardsClient(builder.build(), clock);
  }

  /** The ids in the reply are read, and a film listed twice is returned once. */
  @Test
  @DisplayName("reads the TMDB ids of the winners and removes repeats")
  void readsTheWinnersIds() {
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        .andRespond(withSuccess(TWO_WINNERS, MediaType.APPLICATION_JSON));

    assertThat(client.findWinningMovieIds(OscarCategory.BEST_ACTOR)).containsExactly(872585L, 98L);
  }

  /** Each category asks Wikidata about its own award. */
  @Test
  @DisplayName("asks about the award of the category")
  void asksAboutTheRightAward() {
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        .andExpect(queryParam("query", containsString("Q103916")))
        .andRespond(withSuccess(TWO_WINNERS, MediaType.APPLICATION_JSON));

    client.findWinningMovieIds(OscarCategory.BEST_ACTOR);

    server.verify();
  }

  /** Best Picture belongs to the film itself, so it is asked without a "for work" link. */
  @Test
  @DisplayName("asks about Best Picture on the film, not on a person")
  void bestPictureIsAskedOnTheFilm() {
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        .andExpect(queryParam("query", containsString("Q102427")))
        .andRespond(withSuccess(TWO_WINNERS, MediaType.APPLICATION_JSON));

    client.findWinningMovieIds(OscarCategory.BEST_PICTURE);

    server.verify();
  }

  /**
   * An acting award belongs to a person, but the film is all the search needs. The query goes from
   * the award statement to its "for work" film and never lists people.
   */
  @Test
  @DisplayName("asks for the award statements and their films, without listing people")
  void actingQueryFollowsTheForWorkLink() {
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        // The matcher sees the URL-encoded query: ":" is %3A and "?" is %3F.
        .andExpect(queryParam("query", containsString("pq%3AP1686")))
        .andExpect(queryParam("query", not(containsString("%3Fperson"))))
        .andRespond(withSuccess(TWO_WINNERS, MediaType.APPLICATION_JSON));

    client.findWinningMovieIds(OscarCategory.BEST_ACTRESS);

    server.verify();
  }

  /**
   * A search that arrives while another call is already asking Wikidata about the same category
   * waits for that answer. A second identical question would be slow for Wikidata too, and it
   * throttles a client that asks two at once.
   */
  @Test
  @DisplayName("sends one question when two searches ask for the same category at once")
  void concurrentCallersShareOneQuestion() throws InterruptedException {
    CountDownLatch questionSent = new CountDownLatch(1);
    CountDownLatch answerMayArrive = new CountDownLatch(1);
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        .andRespond(
            request -> {
              questionSent.countDown();
              awaitQuietly(answerMayArrive);
              return withSuccess(TWO_WINNERS, MediaType.APPLICATION_JSON).createResponse(request);
            });
    AtomicReference<List<Long>> firstAnswer = new AtomicReference<>();
    AtomicReference<List<Long>> secondAnswer = new AtomicReference<>();

    // 1. The first caller sends the question and is held while "Wikidata" thinks.
    Thread first =
        Thread.ofVirtual()
            .start(() -> firstAnswer.set(client.findWinningMovieIds(OscarCategory.BEST_ACTOR)));
    assertThat(questionSent.await(5, TimeUnit.SECONDS)).isTrue();

    // 2. The second caller has to wait on the first one's lock, not send its own question.
    Thread second =
        Thread.ofVirtual()
            .start(() -> secondAnswer.set(client.findWinningMovieIds(OscarCategory.BEST_ACTOR)));
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (second.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }

    // 3. The answer arrives; both callers get it from the one question.
    answerMayArrive.countDown();
    first.join(5_000);
    second.join(5_000);

    assertThat(firstAnswer.get()).containsExactly(872585L, 98L);
    assertThat(secondAnswer.get()).containsExactly(872585L, 98L);
    server.verify();
  }

  /**
   * Waits for a latch without failing the test if the wait is cut short.
   *
   * @param latch the latch to wait for, for at most five seconds
   */
  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }

  /** A second lookup within a day is answered from memory. */
  @Test
  @DisplayName("reuses an answer for a day")
  void cachesTheAnswer() {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        .andRespond(withSuccess(TWO_WINNERS, MediaType.APPLICATION_JSON));

    client.findWinningMovieIds(OscarCategory.BEST_ACTOR);
    clock.advance(Duration.ofHours(23));
    List<Long> again = client.findWinningMovieIds(OscarCategory.BEST_ACTOR);

    assertThat(again).containsExactly(872585L, 98L);
    server.verify();
  }

  /** After a day the answer is fetched again, so a new ceremony's winner shows up. */
  @Test
  @DisplayName("asks again after a day")
  void refreshesAfterADay() {
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        .andRespond(withSuccess(TWO_WINNERS, MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        .andRespond(
            withSuccess(
                "{\"results\":{\"bindings\":[{\"tmdb\":{\"value\":\"1233413\"}}]}}",
                MediaType.APPLICATION_JSON));

    client.findWinningMovieIds(OscarCategory.BEST_ACTOR);
    clock.advance(Duration.ofHours(25));

    assertThat(client.findWinningMovieIds(OscarCategory.BEST_ACTOR)).containsExactly(1233413L);
  }

  /** With nothing cached, a Wikidata failure gives no movies rather than an exception. */
  @Test
  @DisplayName("returns nothing when Wikidata fails and nothing is cached")
  void failureWithoutACacheGivesNothing() {
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        .andRespond(withServerError());

    assertThat(client.findWinningMovieIds(OscarCategory.BEST_ACTOR)).isEmpty();
  }

  /** A failure after a day keeps serving the last good answer instead of losing the feature. */
  @Test
  @DisplayName("keeps the last answer when a refresh fails")
  void failureKeepsTheLastAnswer() {
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        .andRespond(withSuccess(TWO_WINNERS, MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        .andRespond(withServerError());

    client.findWinningMovieIds(OscarCategory.BEST_ACTOR);
    clock.advance(Duration.ofHours(25));

    assertThat(client.findWinningMovieIds(OscarCategory.BEST_ACTOR)).containsExactly(872585L, 98L);
  }

  /**
   * One category fails in the first pass. The second pass asks again for that one only: the seven
   * requests are six for the first pass and one for the retry, and an eighth would fail the test
   * because the mock has no expectation left for it.
   */
  @Test
  @DisplayName("warm-up asks again for a category that failed, and only for that one")
  void warmUpRetriesOnlyTheMissingCategory() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    MockRestServiceServer eachOrder =
        MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    AwardsClient warmed = new AwardsClient(builder.build(), clock);
    eachOrder
        .expect(
            ExpectedCount.times(5), requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        .andRespond(withSuccess(TWO_WINNERS, MediaType.APPLICATION_JSON));
    eachOrder
        .expect(once(), requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        .andRespond(withServerError());
    eachOrder
        .expect(once(), requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        .andRespond(withSuccess(TWO_WINNERS, MediaType.APPLICATION_JSON));

    warmed.warmUp();

    eachOrder.verify();
    for (OscarCategory category : OscarCategory.values()) {
      // Every category is now answered from memory: no request is left to send.
      assertThat(warmed.findWinningMovieIds(category)).containsExactly(872585L, 98L);
    }
  }

  /**
   * When Wikidata never answers, the warm-up stops after its passes instead of asking forever:
   * every category is asked once per pass.
   */
  @Test
  @DisplayName("warm-up gives up after its passes when Wikidata never answers")
  void warmUpStopsAfterItsPasses() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    MockRestServiceServer neverAnswers = MockRestServiceServer.bindTo(builder).build();
    AwardsClient warmed = new AwardsClient(builder.build(), clock);
    neverAnswers
        .expect(
            ExpectedCount.times(OscarCategory.values().length * AwardsClient.WARM_UP_PASSES),
            requestTo(org.hamcrest.Matchers.startsWith(BASE + "/sparql")))
        .andRespond(withServerError());

    warmed.warmUp();

    neverAnswers.verify();
  }
}
