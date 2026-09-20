package dev.lmdb.ai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

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
 * @param franchise a movie series named in the query, such as "James Bond", or {@code null}
 * @param keywords other words worth searching for: title words, character names or themes; never
 *     {@code null}
 * @param plainTitle set instead of the fields above when the query carries no detected structured
 *     intent — the caller falls back to a literal title search on this value
 */
public record StructuredQueryFilterDto(
    String personName,
    @JsonFormat(
            with = {
              JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL,
              JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES
            })
        QueryFilterRole role,
    Integer yearFrom,
    Integer yearTo,
    List<String> collaborators,
    String genre,
    List<String> negated,
    String franchise,
    List<String> keywords,
    String plainTitle) {

  /**
   * Builds a filter with no franchise and no keywords. Kept for callers that only know the fields
   * from before franchises and keywords existed.
   *
   * @param personName see the field Javadoc above
   * @param role see the field Javadoc above
   * @param yearFrom see the field Javadoc above
   * @param yearTo see the field Javadoc above
   * @param collaborators see the field Javadoc above
   * @param genre see the field Javadoc above
   * @param negated see the field Javadoc above
   * @param plainTitle see the field Javadoc above
   */
  public StructuredQueryFilterDto(
      String personName,
      QueryFilterRole role,
      Integer yearFrom,
      Integer yearTo,
      List<String> collaborators,
      String genre,
      List<String> negated,
      String plainTitle) {
    this(
        personName,
        role,
        yearFrom,
        yearTo,
        collaborators,
        genre,
        negated,
        null,
        List.of(),
        plainTitle);
  }

  /** Words a model writes in {@code genre} that name no genre. */
  private static final Set<String> NON_GENRES = Set.of("movie", "movies", "film", "films");

  /** Texts a model writes instead of a real JSON null. */
  private static final Set<String> NULL_TEXTS = Set.of("null", "none", "n/a");

  /**
   * Cleans up what the model returned, so the rest of the code can trust the fields.
   *
   * <ul>
   *   <li>{@code collaborators} and {@code negated} become empty lists when omitted.
   *   <li>{@code keywords} becomes an empty list when omitted; blank entries, the text "null" and
   *       repeats are removed.
   *   <li>Blank text, and the text "null", in {@code personName}, {@code genre}, {@code franchise}
   *       and {@code plainTitle} become {@code null}.
   *   <li>A {@code genre} that only says "movie" or "film" becomes {@code null}.
   *   <li>{@code plainTitle} becomes {@code null} when any other field carries a value. It means
   *       "no structure was found", and models often copy the whole query into it anyway.
   * </ul>
   *
   * @param personName see the field Javadoc above
   * @param role see the field Javadoc above; an unknown or "null" value is read as {@code null}
   * @param yearFrom see the field Javadoc above
   * @param yearTo see the field Javadoc above
   * @param collaborators see the field Javadoc above; defaulted to {@link List#of()} when omitted
   * @param genre see the field Javadoc above
   * @param negated see the field Javadoc above; defaulted to {@link List#of()} when omitted
   * @param franchise see the field Javadoc above
   * @param keywords see the field Javadoc above; defaulted to {@link List#of()} when omitted
   * @param plainTitle see the field Javadoc above
   */
  public StructuredQueryFilterDto {
    personName = cleanText(personName);
    genre = cleanText(genre);
    if (genre != null && NON_GENRES.contains(genre.toLowerCase(Locale.ROOT))) {
      genre = null;
    }
    franchise = cleanText(franchise);
    plainTitle = cleanText(plainTitle);
    keywords =
        keywords == null
            ? List.of()
            : keywords.stream()
                .map(StructuredQueryFilterDto::cleanText)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    collaborators = collaborators == null ? List.of() : collaborators;
    negated = negated == null ? List.of() : negated;

    boolean hasStructure =
        hasStructure(
            personName, role, yearFrom, yearTo, collaborators, genre, negated, franchise, keywords);
    if (hasStructure) {
      plainTitle = null;
    }
  }

  /**
   * Tells whether the query was read into any search criterion.
   *
   * @return {@code true} if any field other than {@code plainTitle} has a value
   */
  public boolean hasStructure() {
    return hasStructure(
        personName, role, yearFrom, yearTo, collaborators, genre, negated, franchise, keywords);
  }

  private static boolean hasStructure(
      String personName,
      QueryFilterRole role,
      Integer yearFrom,
      Integer yearTo,
      List<String> collaborators,
      String genre,
      List<String> negated,
      String franchise,
      List<String> keywords) {
    return personName != null
        || role != null
        || yearFrom != null
        || yearTo != null
        || !collaborators.isEmpty()
        || genre != null
        || franchise != null
        || !keywords.isEmpty()
        || !negated.isEmpty();
  }

  /**
   * Trims {@code text} and turns blank text, or a written-out "null", into {@code null}.
   *
   * @param text a text field from the model, possibly {@code null}
   * @return the trimmed text, or {@code null} if it holds no value
   */
  private static String cleanText(String text) {
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    boolean empty = trimmed.isEmpty() || NULL_TEXTS.contains(trimmed.toLowerCase(Locale.ROOT));
    return empty ? null : trimmed;
  }
}
