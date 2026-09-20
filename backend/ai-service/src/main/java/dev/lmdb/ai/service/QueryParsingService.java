package dev.lmdb.ai.service;

import dev.lmdb.ai.dto.QueryParseResponseDto;
import dev.lmdb.ai.dto.StructuredQueryFilterDto;
import dev.lmdb.ai.security.PromptSanitizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Extracts a structured filter (person, role, year range, collaborators, genre, negation) from a
 * free-text, natural-language movie query — or an explicit plain-title fallback when the query
 * carries no detectable structure. Per ADR-020, extraction only: this class does not call
 * actor-service or movie-service and does not execute the filter it produces — that is the
 * cross-service aggregation step (#203), a separate collaborator built on top of this one's output.
 */
@Service
@Slf4j
public class QueryParsingService {

  /** How often the model is asked before the query is searched as a plain title. */
  private static final int MAX_ATTEMPTS = 2;

  private static final String SYSTEM_PROMPT =
      """
        You read a free-text movie search query and fill in a search filter.

        Fields:
        - personName: the main person named (actor, director or producer), or null.
        - role: exactly one of the uppercase words ACTED, DIRECTED, PRODUCED, or null
          if the query does not say.
        - yearFrom, yearTo: release years, either may be null. "after 2000" means
          yearFrom 2000. "in the 1990s" means yearFrom 1990 and yearTo 1999.
        - collaborators: other people who must also be in the movie.
        - genre: a real genre such as Action or Comedy, or null. Never "Movie" or "Film".
        - franchise: a movie series named in the query, such as "James Bond" or
          "Star Wars", or null.
        - keywords: other words worth searching for, such as a title word, a character
          or a theme ("heist", "time travel"). Do not repeat the person, franchise or
          genre here.
        - negated: if the query negates a field (for example "didn't direct"), list
          that field's name here instead of dropping the constraint.
        - plainTitle: only when the query is just a movie title and none of the fields
          above apply. Then leave every other field null or empty.

        Use a real JSON null for a missing value, never the text "null".
        Never invent a person, role, franchise or year the query did not state.

        Examples:
        "movies with Tom Hanks in the 1990s"
          -> personName "Tom Hanks", role "ACTED", yearFrom 1990, yearTo 1999
        "list the James Bond movies after 2000"
          -> franchise "James Bond", yearFrom 2000
        "Daniel Craig as James Bond"
          -> personName "Daniel Craig", franchise "James Bond"
        "heist movies with Brad Pitt"
          -> personName "Brad Pitt", keywords ["heist"]
        "movies Quentin Tarantino didn't direct"
          -> personName "Quentin Tarantino", role "DIRECTED", negated ["role"]
        "Inception"
          -> plainTitle "Inception"

        Respond with exactly one JSON object matching the target schema.
      """;

  private final ChatClient chatClient;
  private final Clock clock;

  /**
   * Builds the service with the system clock.
   *
   * @param chatClientBuilder builder for the Spring AI {@link ChatClient} used to extract the
   *     filter
   */
  @Autowired
  public QueryParsingService(ChatClient.Builder chatClientBuilder) {
    this(chatClientBuilder, Clock.systemDefaultZone());
  }

  /**
   * Builds the service with a given clock, so a test can fix the date that "the last 20 years" is
   * counted from.
   *
   * @param chatClientBuilder builder for the Spring AI {@link ChatClient} used to extract the
   *     filter
   * @param clock the clock that gives today's date
   */
  public QueryParsingService(ChatClient.Builder chatClientBuilder, Clock clock) {
    this.chatClient = chatClientBuilder.build();
    this.clock = clock;
  }

  /**
   * Parses one free-text query into a structured filter.
   *
   * <p><b>Known limitation, stated rather than silently absent</b> (mirrors how ADR-020 documents
   * {@code negated}'s field-grained limit): the returned {@code plainTitle}-only fallback does not
   * distinguish "the model looked at this and legitimately found no structure" from "extraction
   * itself failed" (a malformed/unparseable model response, or a schema mismatch such as the model
   * emitting a lowercase role) — both currently produce the identical 200 response below. That is
   * enough to avoid a raw 500/opaque error, but it is not yet a *confident* signal either way; a
   * caller that needs to tell the two apart would need a real success/failure field added to this
   * contract, which — since the shape is pinned in ADR-020 — is a decision for that ADR to make,
   * not this method to smuggle in unilaterally.
   *
   * @param rawQuery the caller-supplied query text, typed or transcribed from dictation
   * @return the extracted filter; a filter carrying only {@code plainTitle} (set to the sanitized
   *     input) both when the model itself detects no structure and when the model's response can't
   *     be read as the target schema at all — the latter degrades rather than failing the whole
   *     request, the same defensive posture {@link RecommendationService}'s downstream client
   *     applies to a flaky dependency
   */
  public StructuredQueryFilterDto parse(String rawQuery) {
    String sanitized = PromptSanitizer.sanitize(rawQuery);
    log.info("Parsing natural-language query ({} chars)", sanitized.length());

    StructuredQueryFilterDto filter = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS && filter == null; attempt++) {
      try {
        StructuredQueryFilterDto parsed =
            chatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                // No randomness: the same query should always be read the same way.
                .options(ChatOptions.builder().temperature(0.0))
                .user(sanitized)
                .call()
                .entity(StructuredQueryFilterDto.class);
        filter =
            usable(
                parsed == null
                    ? null
                    : FilterGrounding.ground(parsed, sanitized, LocalDate.now(clock)));
        if (filter == null) {
          log.warn("Query-parsing reply had nothing the query supports (attempt {})", attempt);
        }
      } catch (Exception e) {
        // The reply was not valid JSON for the schema, for example cut off half way. The model
        // answers differently each time, so one more attempt usually works.
        log.warn(
            "Query-parsing model call failed (attempt {} of {}): {}",
            attempt,
            MAX_ATTEMPTS,
            e.getMessage());
      }
    }

    return filter == null ? plainTitleFallback(sanitized) : filter;
  }

  /**
   * Parses one free-text query into a structured filter alongside its token/span breakdown (#207) —
   * the shape {@code POST /api/v1/ai/search/query} actually returns. Spans are computed
   * deterministically from the already-extracted filter by {@link QuerySpanExtractor}, not
   * requested from the model itself — see that class's own Javadoc for why.
   *
   * <p>Sanitizes {@code rawQuery} once here and reuses that exact string for both extraction (via
   * {@link #parse}, which re-sanitizes — a no-op on already-sanitized text) and span offsets, so a
   * span's {@code start}/{@code end} always line up with the same text the model itself saw.
   *
   * @param rawQuery the caller-supplied query text, typed or transcribed from dictation
   * @return the extracted filter (or plain-title fallback) and its span breakdown
   */
  public QueryParseResponseDto parseWithSpans(String rawQuery) {
    String sanitized = PromptSanitizer.sanitize(rawQuery);
    StructuredQueryFilterDto filter = parse(sanitized);
    return new QueryParseResponseDto(filter, QuerySpanExtractor.extract(sanitized, filter));
  }

  /**
   * Accepts a filter only if it says something: some structure, or a plain title.
   *
   * @param filter a grounded filter, or {@code null}
   * @return the filter, or {@code null} if it is empty or {@code null}
   */
  private static StructuredQueryFilterDto usable(StructuredQueryFilterDto filter) {
    boolean hasContent = filter != null && (filter.hasStructure() || filter.plainTitle() != null);
    return hasContent ? filter : null;
  }

  /**
   * Builds the filter returned when the model detected no structure, or its response couldn't be
   * read as one — the sanitized input, treated as a literal title.
   *
   * @param sanitizedQuery the already-sanitized query text
   * @return a filter with every structured field null/empty and {@code plainTitle} set to {@code
   *     sanitizedQuery}
   */
  private static StructuredQueryFilterDto plainTitleFallback(String sanitizedQuery) {
    return new StructuredQueryFilterDto(
        null, null, null, null, List.of(), null, List.of(), sanitizedQuery);
  }
}
