package dev.lmdb.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.lmdb.ai.dto.OscarCategory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Looks up which movies won an Academy Award, using Wikidata's public SPARQL service. TMDB has no
 * award data. Wikidata lists each winning film with its TMDB id, so the result joins straight into
 * the movie catalog.
 *
 * <p>The answer for a category changes once a year, so it is kept for {@link #CACHE_TTL}. If
 * Wikidata cannot be reached, the last answer is used, or none: an award lookup never fails the
 * search that asked for it.
 */
@Component
@Slf4j
public class AwardsClient {

  /** How long a category's answer is reused. */
  static final Duration CACHE_TTL = Duration.ofHours(24);

  /** How many times the warm-up goes through the categories that still have no answer. */
  static final int WARM_UP_PASSES = 3;

  /** Wikidata ids of the award for each category. */
  private static final Map<OscarCategory, String> AWARD_IDS =
      Map.of(
          OscarCategory.BEST_PICTURE, "Q102427",
          OscarCategory.BEST_ACTOR, "Q103916",
          OscarCategory.BEST_ACTRESS, "Q103618",
          OscarCategory.BEST_SUPPORTING_ACTOR, "Q106291",
          OscarCategory.BEST_SUPPORTING_ACTRESS, "Q106301",
          OscarCategory.BEST_DIRECTOR, "Q103360");

  /**
   * One category's answer and when it was fetched.
   *
   * @param movieIds the winning movies' TMDB ids
   * @param fetchedAt when Wikidata was asked
   */
  private record Cached(List<Long> movieIds, Instant fetchedAt) {}

  /**
   * The parts of a SPARQL JSON reply that are read.
   *
   * @param results the result table
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record SparqlReply(Results results) {

    /**
     * The rows of the result table.
     *
     * @param bindings one entry per row
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Results(List<Map<String, Value>> bindings) {}

    /**
     * One cell of a row.
     *
     * @param value the cell's text
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Value(String value) {}
  }

  private final RestClient restClient;
  private final Clock clock;
  private final Map<OscarCategory, Cached> cache = new ConcurrentHashMap<>();
  private final Map<OscarCategory, ReentrantLock> locks = new EnumMap<>(OscarCategory.class);

  /**
   * Builds the client.
   *
   * @param awardsRestClient the client for the Wikidata query service
   */
  @Autowired
  public AwardsClient(@Qualifier("awardsRestClient") RestClient awardsRestClient) {
    this(awardsRestClient, Clock.systemUTC());
  }

  /**
   * Builds the client with a given clock, so a test can move time.
   *
   * @param awardsRestClient the client for the Wikidata query service
   * @param clock the clock that decides when a cached answer is too old
   */
  AwardsClient(RestClient awardsRestClient, Clock clock) {
    this.restClient = awardsRestClient;
    this.clock = clock;
    for (OscarCategory category : OscarCategory.values()) {
      locks.put(category, new ReentrantLock());
    }
  }

  /**
   * Fetches every category once, in the background, when the service has started. Wikidata can take
   * up to a minute for a category, so the first search that asks for an award should find the
   * answer already cached.
   */
  @EventListener(ApplicationReadyEvent.class)
  void warmUpInBackground() {
    Thread.startVirtualThread(this::warmUp);
  }

  /**
   * Fetches every category and keeps the answers. A category that has no answer yet is asked again
   * in the next pass, up to {@link #WARM_UP_PASSES} passes: Wikidata keeps working on a query after
   * the client stops waiting, so the second ask is usually fast. A category that still has no
   * answer is asked again when a search needs it.
   */
  void warmUp() {
    for (int pass = 1; pass <= WARM_UP_PASSES; pass++) {
      boolean missing = false;
      for (OscarCategory category : OscarCategory.values()) {
        missing |= findWinningMovieIds(category).isEmpty();
      }
      if (!missing) {
        return;
      }
    }
  }

  /**
   * Finds the movies that won an Oscar category.
   *
   * @param category the category
   * @return TMDB ids of the winning movies; empty if Wikidata knows none or cannot be reached and
   *     nothing was fetched before
   */
  public List<Long> findWinningMovieIds(OscarCategory category) {
    Optional<List<Long>> fresh = freshAnswer(category);
    if (fresh.isPresent()) {
      return fresh.get();
    }
    // One question per category at a time: a search that arrives while the warm-up is still asking
    // Wikidata waits for that answer, instead of sending the same slow query again.
    ReentrantLock lock = locks.get(category);
    lock.lock();
    try {
      // The call that held the lock may have just stored the answer.
      return freshAnswer(category).orElseGet(() -> fetch(category));
    } finally {
      lock.unlock();
    }
  }

  /**
   * Reads the kept answer for a category if it is still recent enough.
   *
   * @param category the category
   * @return the kept movie ids, or empty if there is none or it is older than {@link #CACHE_TTL}
   */
  private Optional<List<Long>> freshAnswer(OscarCategory category) {
    Cached cached = cache.get(category);
    if (cached == null
        || Duration.between(cached.fetchedAt(), clock.instant()).compareTo(CACHE_TTL) >= 0) {
      return Optional.empty();
    }
    return Optional.of(cached.movieIds());
  }

  /**
   * Asks Wikidata and keeps a non-empty answer.
   *
   * @param category the category
   * @return the winners' TMDB ids; the last kept answer, or none, if Wikidata fails or knows none
   */
  private List<Long> fetch(OscarCategory category) {
    Cached cached = cache.get(category);
    Instant now = clock.instant();
    try {
      List<Long> ids = query(category);
      if (!ids.isEmpty()) {
        cache.put(category, new Cached(ids, now));
      }
      return ids.isEmpty() && cached != null ? cached.movieIds() : ids;
    } catch (Exception e) {
      log.warn("Wikidata award lookup failed for {}: {}", category, describe(e));
      return cached == null ? List.of() : cached.movieIds();
    }
  }

  /**
   * Writes an exception and its causes on one line, so a log line shows why a call really failed.
   *
   * @param error the exception
   * @return each exception's class and message, from the outermost to the root cause
   */
  private static String describe(Throwable error) {
    StringBuilder text = new StringBuilder();
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (text.length() > 0) {
        text.append(" <- ");
      }
      text.append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
    }
    return text.toString();
  }

  /**
   * Asks Wikidata for the winners of a category.
   *
   * @param category the category
   * @return the TMDB ids in the reply
   */
  private List<Long> query(OscarCategory category) {
    SparqlReply reply =
        restClient
            .get()
            .uri(
                uriBuilder ->
                    // The query text has braces, so it goes in as a template variable that is
                    // encoded, not as literal text that would be read as a placeholder.
                    uriBuilder
                        .path("/sparql")
                        .queryParam("format", "json")
                        .queryParam("query", "{query}")
                        .build(sparqlFor(category)))
            .header("Accept", "application/sparql-results+json")
            .retrieve()
            .body(SparqlReply.class);
    if (reply == null || reply.results() == null || reply.results().bindings() == null) {
      return List.of();
    }
    return reply.results().bindings().stream()
        .map(row -> row.get("tmdb"))
        .filter(cell -> cell != null && cell.value() != null)
        .map(cell -> Long.valueOf(cell.value()))
        .distinct()
        .toList();
  }

  /**
   * Writes the query for a category. A film wins Best Picture itself. For the acting and directing
   * categories the award belongs to a person: the query starts from the award statements and
   * follows each one's "for work" qualifier to the film, so it never lists the people.
   *
   * @param category the category
   * @return the SPARQL text
   */
  private static String sparqlFor(OscarCategory category) {
    String award = AWARD_IDS.get(category);
    if (category == OscarCategory.BEST_PICTURE) {
      return "SELECT DISTINCT ?tmdb WHERE { ?film wdt:P166 wd:"
          + award
          + " . ?film wdt:P4947 ?tmdb . }";
    }
    return "SELECT DISTINCT ?tmdb WHERE { ?statement ps:P166 wd:"
        + award
        + " ; pq:P1686 ?film . ?film wdt:P4947 ?tmdb . }";
  }
}
