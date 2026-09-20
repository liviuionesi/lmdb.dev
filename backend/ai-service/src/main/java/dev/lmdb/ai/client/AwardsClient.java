package dev.lmdb.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.lmdb.ai.dto.OscarCategory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
  }

  /**
   * Finds the movies that won an Oscar category.
   *
   * @param category the category
   * @return TMDB ids of the winning movies; empty if Wikidata knows none or cannot be reached and
   *     nothing was fetched before
   */
  public List<Long> findWinningMovieIds(OscarCategory category) {
    Cached cached = cache.get(category);
    Instant now = clock.instant();
    if (cached != null && Duration.between(cached.fetchedAt(), now).compareTo(CACHE_TTL) < 0) {
      return cached.movieIds();
    }
    try {
      List<Long> ids = query(category);
      if (!ids.isEmpty()) {
        cache.put(category, new Cached(ids, now));
      }
      return ids.isEmpty() && cached != null ? cached.movieIds() : ids;
    } catch (Exception e) {
      log.warn("Wikidata award lookup failed for {}: {}", category, e.getMessage());
      return cached == null ? List.of() : cached.movieIds();
    }
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
   * categories the award belongs to a person, and the "for work" qualifier names the film.
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
    return "SELECT DISTINCT ?tmdb WHERE { ?person p:P166 ?award . ?award ps:P166 wd:"
        + award
        + " ; pq:P1686 ?film . ?film wdt:P4947 ?tmdb . }";
  }
}
