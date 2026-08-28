package dev.lmdb.ai.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/v1/ai/search/query} — a structured filter extracted from a
 * free-text movie query, or an explicit plain-title fallback when the query carries no detectable
 * structure. Shape agreed in ADR-020 (see {@code
 * docs/architecture/adr/020-nl-query-cross-service-aggregation.md}) and reused as-is by the
 * cross-service aggregation step (#203) and Story #199's search-bar highlighting (#207).
 *
 * <p>{@code negated} is field-grained, not per-value: it can express "didn't direct" (via {@code
 * ["role"]}) but not "with X but not Y" inside a multi-name {@code collaborators} list — a stated
 * limitation of the ADR, not an oversight here.
 *
 * @param personName the primary subject's name, or {@code null} if the query names no person
 * @param role how {@code personName} relates to the movie, or {@code null} if the query doesn't
 *     specify one
 * @param yearFrom inclusive start of a release-year range, or {@code null}
 * @param yearTo inclusive end of a release-year range, or {@code null}
 * @param collaborators other people the query additionally requires credited on the same movie;
 *     never {@code null}
 * @param genre a genre named in the query, or {@code null}
 * @param negated field names this query negates (e.g. {@code "role"} for "didn't direct"); never
 *     {@code null}
 * @param plainTitle set instead of the fields above when the query carries no detected structured
 *     intent — the caller falls back to a literal title search on this value
 */
public record StructuredQueryFilterDto(
    String personName,
    QueryFilterRole role,
    Integer yearFrom,
    Integer yearTo,
    List<String> collaborators,
    String genre,
    List<String> negated,
    String plainTitle) {

  /**
   * Normalizes {@code collaborators}/{@code negated} to an empty list when the model's JSON
   * response simply omits either key. Jackson leaves an omitted record component {@code null}
   * rather than failing deserialization, so an incomplete-but-valid model response isn't caught by
   * {@link dev.lmdb.ai.service.QueryParsingService#parse}'s parse-failure handling — without this,
   * this record's own "never {@code null}" contract on those two fields could be silently violated
   * on the ordinary success path, not just avoided via the explicit fallback.
   *
   * @param personName see the field Javadoc above
   * @param role see the field Javadoc above
   * @param yearFrom see the field Javadoc above
   * @param yearTo see the field Javadoc above
   * @param collaborators see the field Javadoc above; defaulted to {@link List#of()} when omitted
   * @param genre see the field Javadoc above
   * @param negated see the field Javadoc above; defaulted to {@link List#of()} when omitted
   * @param plainTitle see the field Javadoc above
   */
  public StructuredQueryFilterDto {
    collaborators = collaborators == null ? List.of() : collaborators;
    negated = negated == null ? List.of() : negated;
  }
}
