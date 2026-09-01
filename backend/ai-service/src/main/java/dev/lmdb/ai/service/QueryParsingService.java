package dev.lmdb.ai.service;

import dev.lmdb.ai.dto.QueryParseResponseDto;
import dev.lmdb.ai.dto.StructuredQueryFilterDto;
import dev.lmdb.ai.security.PromptSanitizer;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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

  private static final String SYSTEM_PROMPT =
      """
        You extract a structured search filter from a free-text movie query.
        Identify: the primary person named (personName), their role relative
        to the movie if stated (role — exactly one of the literal strings
        ACTED, DIRECTED, PRODUCED, uppercase, or null if unstated — never a
        lowercase or mixed-case variant), a release-year range (yearFrom/
        yearTo, either may be null), any other named people the query also
        requires credited on the same movie (collaborators), and a genre if
        named.

        Negation: if the query negates a field (e.g. "didn't direct", "not
        starring"), list that field's name in "negated" instead of silently
        dropping the constraint or treating it as a positive match.

        If the query is just a title with no person, role, date range, or
        collaborator constraint, set "plainTitle" to that title and leave
        every other field null/empty rather than guessing at structure that
        isn't there.

        Respond with exactly one JSON object matching the target schema.
        Never invent a person, role, or year the query didn't state.
        """;

  private final ChatClient chatClient;

  /**
   * @param chatClientBuilder builder for the Spring AI {@link ChatClient} used to extract the
   *     filter
   */
  public QueryParsingService(ChatClient.Builder chatClientBuilder) {
    this.chatClient = chatClientBuilder.build();
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

    StructuredQueryFilterDto filter;
    try {
      filter =
          chatClient
              .prompt()
              .system(SYSTEM_PROMPT)
              .user(sanitized)
              .call()
              .entity(StructuredQueryFilterDto.class);
    } catch (Exception e) {
      // The model's response didn't match the target schema (ambiguous/malformed output, or a
      // schema mismatch like a lowercase role value). Falling back here — instead of letting this
      // propagate to a 500 — gives the caller a usable plain-title result instead of an opaque
      // error, though see the "Known limitation" note in this method's own Javadoc above: this
      // path is not distinguishable from "the model found no structure" by the caller today.
      log.warn("Query-parsing model call failed, falling back to plain title: {}", e.getMessage());
      filter = null;
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
