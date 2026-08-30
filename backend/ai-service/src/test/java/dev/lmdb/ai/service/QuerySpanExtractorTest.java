package dev.lmdb.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lmdb.ai.dto.QueryFilterRole;
import dev.lmdb.ai.dto.QuerySpanCategory;
import dev.lmdb.ai.dto.QuerySpanDto;
import dev.lmdb.ai.dto.StructuredQueryFilterDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link QuerySpanExtractor}, the deterministic (non-LLM) span-offset computation behind
 * {@code POST /api/v1/ai/search/query}'s span breakdown (#207, ADR-020). Package-private class, so
 * these tests live in the same package to reach it directly — no Spring context, no mocks, since
 * the class under test is pure string search over an already-built {@link
 * StructuredQueryFilterDto}.
 */
@DisplayName("QuerySpanExtractor Tests")
class QuerySpanExtractorTest {

  /**
   * Given a query naming a person, a year range, and a collaborator joined by "and", when spans are
   * extracted, then the result carries one CONNECTOR span for "and" and one ENTITY span per named
   * value, each at its exact offset and in left-to-right text order — #207 AC1/AC4's multi-category
   * case, and the boundary between "which span is which" that a wrong category assignment or an
   * off-by-one offset would silently break.
   */
  @Test
  @DisplayName("Extracts one CONNECTOR span and one ENTITY span per value, in text order")
  void extract_MultiCategoryQuery_ReturnsSpansInTextOrder() {
    // Given: a query text and the filter #202's extraction step would have produced from it
    String text = "movies Tom Hanks directed between 2000 and 2010 that also starred Meg Ryan";
    StructuredQueryFilterDto filter =
        new StructuredQueryFilterDto(
            "Tom Hanks",
            QueryFilterRole.DIRECTED,
            2000,
            2010,
            List.of("Meg Ryan"),
            null,
            List.of(),
            null);

    // When
    List<QuerySpanDto> spans = QuerySpanExtractor.extract(text, filter);

    // Then: 5 spans, sorted by start — personName, yearFrom, "and", yearTo, collaborator
    assertThat(spans).hasSize(5);
    assertThat(spans.get(0))
        .isEqualTo(new QuerySpanDto("Tom Hanks", QuerySpanCategory.ENTITY, 7, 16));
    assertThat(spans.get(1)).isEqualTo(new QuerySpanDto("2000", QuerySpanCategory.ENTITY, 34, 38));
    assertThat(spans.get(2))
        .isEqualTo(new QuerySpanDto("and", QuerySpanCategory.CONNECTOR, 39, 42));
    assertThat(spans.get(3)).isEqualTo(new QuerySpanDto("2010", QuerySpanCategory.ENTITY, 43, 47));
    assertThat(spans.get(4))
        .isEqualTo(new QuerySpanDto("Meg Ryan", QuerySpanCategory.ENTITY, 66, 74));
  }

  /**
   * Given a query using the contraction form of negation ("didn't direct"), when spans are
   * extracted, then the NEGATION span covers the whole cue-plus-verb phrase, not just "didn't" —
   * the exact example #199's own acceptance criteria names.
   */
  @Test
  @DisplayName("Extends a contraction negation cue to cover the verb it negates")
  void extract_ContractionNegation_SpanCoversCueAndVerb() {
    // Given
    String text = "movies Clint Eastwood didn't direct";
    StructuredQueryFilterDto filter =
        new StructuredQueryFilterDto(
            "Clint Eastwood",
            QueryFilterRole.DIRECTED,
            null,
            null,
            List.of(),
            null,
            List.of("role"),
            null);

    // When
    List<QuerySpanDto> spans = QuerySpanExtractor.extract(text, filter);

    // Then
    assertThat(spans)
        .filteredOn(span -> span.category() == QuerySpanCategory.NEGATION)
        .containsExactly(new QuerySpanDto("didn't direct", QuerySpanCategory.NEGATION, 22, 35));
  }

  /**
   * Given a query using the two-word negation form ("not starring") rather than a contraction, when
   * spans are extracted, then the same cue-plus-verb extension applies — proves the extension logic
   * isn't accidentally coupled to the apostrophe in "didn't".
   */
  @Test
  @DisplayName("Extends a two-word negation cue to cover the verb it negates")
  void extract_TwoWordNegation_SpanCoversCueAndVerb() {
    // Given
    String text = "movies not starring Tom Hanks";
    StructuredQueryFilterDto filter =
        new StructuredQueryFilterDto(
            "Tom Hanks", QueryFilterRole.ACTED, null, null, List.of(), null, List.of("role"), null);

    // When
    List<QuerySpanDto> spans = QuerySpanExtractor.extract(text, filter);

    // Then
    assertThat(spans)
        .filteredOn(span -> span.category() == QuerySpanCategory.NEGATION)
        .containsExactly(new QuerySpanDto("not starring", QuerySpanCategory.NEGATION, 7, 19));
  }

  /**
   * Given a query naming a person whose name contains non-ASCII (accented) characters, when spans
   * are extracted, then the ENTITY span's offsets are exact — #207 AC2's own stated verification
   * case. A boundary check that only understood ASCII word characters would misplace this span's
   * edges around the accented letters.
   */
  @Test
  @DisplayName("Produces exact offsets for a name containing accented characters")
  void extract_AccentedName_ProducesExactOffsets() {
    // Given
    String text = "movies directed by François Truffaut";
    StructuredQueryFilterDto filter =
        new StructuredQueryFilterDto(
            "François Truffaut",
            QueryFilterRole.DIRECTED,
            null,
            null,
            List.of(),
            null,
            List.of(),
            null);

    // When
    List<QuerySpanDto> spans = QuerySpanExtractor.extract(text, filter);

    // Then
    assertThat(spans)
        .containsExactly(new QuerySpanDto("François Truffaut", QuerySpanCategory.ENTITY, 19, 36));
  }

  /**
   * Given a plain-title query (no structured intent detected), when spans are extracted, then the
   * result is empty rather than highlighting stray words like "and" inside the title itself — #207
   * AC3: a plain title carries no operator/entity structure to highlight.
   */
  @Test
  @DisplayName("Returns no spans for a plain-title query, even one containing 'and'")
  void extract_PlainTitleQuery_ReturnsEmptyList() {
    // Given: a title that itself contains a connector-looking word
    String text = "Fast and Furious";
    StructuredQueryFilterDto filter =
        new StructuredQueryFilterDto(null, null, null, null, List.of(), null, List.of(), text);

    // When
    List<QuerySpanDto> spans = QuerySpanExtractor.extract(text, filter);

    // Then
    assertThat(spans).isEmpty();
  }

  /**
   * Given a filter naming a person whose name is a strict substring of a longer word actually
   * present in the text (not a standalone occurrence of the name itself), when spans are extracted,
   * then no ENTITY span is produced for it — proves the word-boundary check actually rejects a
   * partial-word match rather than merely happening not to hit one in the happier tests above.
   */
  @Test
  @DisplayName("Does not match an entity value that only appears as part of a longer word")
  void extract_EntityValueOnlyAppearsInsideALongerWord_ProducesNoSpanForIt() {
    // Given: "Ann" is a substring of "Anna" but never occurs as its own word in this text
    String text = "movies starring Anna Kendrick";
    StructuredQueryFilterDto filter =
        new StructuredQueryFilterDto(
            "Ann", QueryFilterRole.ACTED, null, null, List.of(), null, List.of(), null);

    // When
    List<QuerySpanDto> spans = QuerySpanExtractor.extract(text, filter);

    // Then
    assertThat(spans).isEmpty();
  }

  /**
   * Given a filter with no negated fields at all, when spans are extracted, then no NEGATION span
   * is produced even if the raw text happens to contain a negation-cue-looking word — the cue
   * search is gated on {@code negated} being non-empty specifically so a stray "not" in an
   * otherwise-positive query isn't misread as a negation.
   */
  @Test
  @DisplayName("Does not produce a NEGATION span when nothing was actually negated")
  void extract_NoFieldNegated_ProducesNoNegationSpanEvenIfCueWordPresent() {
    // Given: "not" appears in the text, but nothing in the filter is negated
    String text = "movies Tom Hanks directed, not that it matters";
    StructuredQueryFilterDto filter =
        new StructuredQueryFilterDto(
            "Tom Hanks", QueryFilterRole.DIRECTED, null, null, List.of(), null, List.of(), null);

    // When
    List<QuerySpanDto> spans = QuerySpanExtractor.extract(text, filter);

    // Then
    assertThat(spans).noneMatch(span -> span.category() == QuerySpanCategory.NEGATION);
  }

  /**
   * Given a query joining two constraints with "or" rather than "and", when spans are extracted,
   * then a CONNECTOR span is produced for it at its exact offset — the connector pattern's "or"
   * alternative has no other test coverage, so a broken/removed "or" branch would otherwise go
   * undetected (#207 AC1 names both "and" and "or").
   */
  @Test
  @DisplayName("Extracts a CONNECTOR span for 'or', not just 'and'")
  void extract_OrConnector_ReturnsConnectorSpan() {
    // Given
    String text = "movies starring Tom Hanks or Meg Ryan";
    StructuredQueryFilterDto filter =
        new StructuredQueryFilterDto(
            "Tom Hanks",
            QueryFilterRole.ACTED,
            null,
            null,
            List.of("Meg Ryan"),
            null,
            List.of(),
            null);

    // When
    List<QuerySpanDto> spans = QuerySpanExtractor.extract(text, filter);

    // Then
    assertThat(spans)
        .filteredOn(span -> span.category() == QuerySpanCategory.CONNECTOR)
        .containsExactly(new QuerySpanDto("or", QuerySpanCategory.CONNECTOR, 26, 28));
  }

  /**
   * Given a query containing two separate connector words, when spans are extracted, then both
   * produce their own CONNECTOR span — proves {@code connectorSpans}' {@code while
   * (matcher.find())} loop actually keeps matching past the first hit, rather than a regression to
   * a single {@code if} silently dropping every occurrence after the first.
   */
  @Test
  @DisplayName("Extracts one CONNECTOR span per occurrence when a query has more than one")
  void extract_MultipleConnectorsInOneQuery_ReturnsOneSpanEach() {
    // Given
    String text = "movies with Tom Hanks and Meg Ryan or Rita Wilson";
    StructuredQueryFilterDto filter =
        new StructuredQueryFilterDto(
            "Tom Hanks",
            QueryFilterRole.ACTED,
            null,
            null,
            List.of("Meg Ryan", "Rita Wilson"),
            null,
            List.of(),
            null);

    // When
    List<QuerySpanDto> spans = QuerySpanExtractor.extract(text, filter);

    // Then
    assertThat(spans)
        .filteredOn(span -> span.category() == QuerySpanCategory.CONNECTOR)
        .containsExactly(
            new QuerySpanDto("and", QuerySpanCategory.CONNECTOR, 22, 25),
            new QuerySpanDto("or", QuerySpanCategory.CONNECTOR, 35, 37));
  }

  /**
   * Given a query naming a genre, when spans are extracted, then an ENTITY span is produced for it
   * — {@code entitySpans}' {@code filter.genre()} call (#207 AC1 names genre as one of the four
   * entity kinds) otherwise has zero coverage across this suite, since every other filter here
   * leaves {@code genre} {@code null}.
   */
  @Test
  @DisplayName("Extracts an ENTITY span for a named genre")
  void extract_Genre_ReturnsEntitySpan() {
    // Given
    String text = "horror movies from the 1980s";
    StructuredQueryFilterDto filter =
        new StructuredQueryFilterDto(null, null, 1980, 1989, List.of(), "horror", List.of(), null);

    // When
    List<QuerySpanDto> spans = QuerySpanExtractor.extract(text, filter);

    // Then
    assertThat(spans)
        .filteredOn(span -> span.category() == QuerySpanCategory.ENTITY)
        .contains(new QuerySpanDto("horror", QuerySpanCategory.ENTITY, 0, 6));
  }

  /**
   * Given a negated genre whose value sits immediately after its cue word with nothing else between
   * them ("not horror"), when spans are extracted, then the NEGATION span stops at "not" instead of
   * absorbing "horror" too — otherwise the NEGATION span (extended to cover "not horror") would
   * fully overlap the genre's own ENTITY span for "horror", handing the frontend two different
   * categories for the same characters. Distinct from the existing negation tests, which only cover
   * {@code role} negation — a field that never produces an ENTITY span of its own to collide with.
   */
  @Test
  @DisplayName("Does not let a negation span absorb an adjacent entity's own span")
  void extract_NegatedGenreAdjacentToCue_NegationSpanDoesNotOverlapEntitySpan() {
    // Given
    String text = "movies that are not horror";
    StructuredQueryFilterDto filter =
        new StructuredQueryFilterDto(
            null, null, null, null, List.of(), "horror", List.of("genre"), null);

    // When
    List<QuerySpanDto> spans = QuerySpanExtractor.extract(text, filter);

    // Then: the genre still gets its own exact ENTITY span, and the NEGATION span stops at "not"
    // instead of extending into (and overlapping) it
    assertThat(spans)
        .containsExactly(
            new QuerySpanDto("not", QuerySpanCategory.NEGATION, 16, 19),
            new QuerySpanDto("horror", QuerySpanCategory.ENTITY, 20, 26));
  }

  /**
   * Given two distinct filter fields that happen to resolve to the same literal value (a single-
   * year query where {@code yearFrom} and {@code yearTo} both land on the same year), when spans
   * are extracted, then only one ENTITY span is produced for that value — not one per field that
   * named it — since {@code findWordSpan} always finds the same first occurrence for both, and
   * without de-duplication the result would carry two byte-identical spans for the same characters.
   */
  @Test
  @DisplayName("Does not duplicate a span when two fields resolve to the same value")
  void extract_TwoFieldsShareSameValue_ReturnsSingleSpanForIt() {
    // Given: yearFrom and yearTo both 2010, e.g. an exact-year query
    String text = "movies from 2010";
    StructuredQueryFilterDto filter =
        new StructuredQueryFilterDto(null, null, 2010, 2010, List.of(), null, List.of(), null);

    // When
    List<QuerySpanDto> spans = QuerySpanExtractor.extract(text, filter);

    // Then
    assertThat(spans).containsExactly(new QuerySpanDto("2010", QuerySpanCategory.ENTITY, 12, 16));
  }
}
