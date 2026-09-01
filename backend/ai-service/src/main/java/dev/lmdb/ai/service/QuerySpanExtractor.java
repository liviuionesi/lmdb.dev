package dev.lmdb.ai.service;

import dev.lmdb.ai.dto.QuerySpanCategory;
import dev.lmdb.ai.dto.QuerySpanDto;
import dev.lmdb.ai.dto.StructuredQueryFilterDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Computes a {@link QuerySpanDto} breakdown for a natural-language query, given the {@link
 * StructuredQueryFilterDto} already extracted from it (#207, ADR-020). Deliberately not asked of
 * the model in {@link QueryParsingService}'s own prompt: span offsets must be exact against the
 * original submitted text (#207 AC2), and a generative model has no reliable way to report its own
 * output's character positions in text it was only shown, not asked to index. Instead, this class
 * locates each already-extracted value (person, collaborator, genre, year, connector and negation
 * words) back in the original text with plain, deterministic string search — exact by construction,
 * not by the model's cooperation.
 */
final class QuerySpanExtractor {

  /**
   * Boolean connector words highlighted whenever a structured (non-plain-title) filter is present.
   */
  private static final Pattern CONNECTOR_PATTERN =
      Pattern.compile("\\b(?:and|or)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /**
   * Negation cue phrases, longest/most-specific first. Order matters only when two alternatives
   * could both start matching at the very same text position (e.g. "did not" vs. "not") — {@link
   * #findWordSpan} always returns the leftmost match, so a shorter cue occurring earlier in the
   * text is never shadowed by a longer one listed first; this ordering exists purely so that, at
   * one shared start position, the more specific phrase (not just its trailing "not") is what gets
   * matched.
   */
  private static final List<String> NEGATION_CUES =
      List.of(
          "did not",
          "didn't",
          "does not",
          "doesn't",
          "was not",
          "wasn't",
          "is not",
          "isn't",
          "has not",
          "hasn't",
          "have not",
          "haven't",
          "no longer",
          "never",
          "not");

  /** Static-only utility. */
  private QuerySpanExtractor() {}

  /**
   * Builds the full span list for one query.
   *
   * @param text the exact text the spans are offset against (the sanitized query {@link
   *     QueryParsingService#parseWithSpans} also handed the model)
   * @param filter the structured filter already extracted from {@code text}
   * @return spans in ascending order of {@link QuerySpanDto#start}; empty for a plain-title query
   *     (#207 AC3) — a plain title carries no operator/entity structure to highlight
   */
  static List<QuerySpanDto> extract(String text, StructuredQueryFilterDto filter) {
    if (filter.plainTitle() != null) {
      return List.of();
    }

    // 1. Entities first: negation's own extension (below) needs to know where an entity value
    //    starts, so it doesn't swallow one (e.g. "not horror" must not absorb the "horror" genre
    //    entity into the negation span too).
    List<QuerySpanDto> entitySpans = entitySpans(text, filter);
    Set<Integer> entityStarts = new HashSet<>();
    for (QuerySpanDto span : entitySpans) {
      entityStarts.add(span.start());
    }

    // 2. Collect every category, then de-duplicate identical (start, end) pairs — two filter
    //    fields can share one literal value (e.g. yearFrom == yearTo), which would otherwise
    //    surface as two byte-identical spans for the same characters.
    Set<QuerySpanDto> spans = new LinkedHashSet<>();
    spans.addAll(connectorSpans(text));
    negationSpan(text, filter, entityStarts).ifPresent(spans::add);
    spans.addAll(entitySpans);

    List<QuerySpanDto> sorted = new ArrayList<>(spans);
    sorted.sort(Comparator.comparingInt(QuerySpanDto::start));
    return List.copyOf(sorted);
  }

  /**
   * @param text the query text
   * @return one CONNECTOR span per whole-word "and"/"or" occurrence, in text order
   */
  private static List<QuerySpanDto> connectorSpans(String text) {
    List<QuerySpanDto> spans = new ArrayList<>();
    Matcher matcher = CONNECTOR_PATTERN.matcher(text);
    while (matcher.find()) {
      spans.add(
          new QuerySpanDto(
              text.substring(matcher.start(), matcher.end()),
              QuerySpanCategory.CONNECTOR,
              matcher.start(),
              matcher.end()));
    }
    return spans;
  }

  /**
   * Finds the negation cue this query actually used and extends it to cover the verb it negates,
   * matching #207 AC's own examples: "not" alone becomes "not starring", "didn't" becomes "didn't
   * direct". Only ever produces at most one span, mirroring {@code negated}'s own field-grained
   * (not per-occurrence) limitation, stated in ADR-020 and honored as-is by #203's aggregation.
   *
   * @param text the query text
   * @param filter the extracted filter; only consulted for whether anything was negated at all —
   *     which field doesn't change how the cue phrase itself is located
   * @param entityStarts start offsets of every already-computed ENTITY span, so the extension below
   *     never swallows one whole (e.g. a negated genre sitting right after its cue: "not horror"
   *     must not absorb the "horror" ENTITY span into the NEGATION span too — the two would then
   *     overlap and disagree on category for the same characters)
   * @return the negation span, if {@code filter.negated()} is non-empty and a cue phrase is
   *     actually present in {@code text}
   */
  private static Optional<QuerySpanDto> negationSpan(
      String text, StructuredQueryFilterDto filter, Set<Integer> entityStarts) {
    if (filter.negated().isEmpty()) {
      return Optional.empty();
    }
    for (String cue : NEGATION_CUES) {
      int[] cueSpan = findWordSpan(text, cue);
      if (cueSpan != null) {
        int end = extendToFollowingWord(text, cueSpan[1], entityStarts);
        return Optional.of(
            new QuerySpanDto(
                text.substring(cueSpan[0], end), QuerySpanCategory.NEGATION, cueSpan[0], end));
      }
    }
    return Optional.empty();
  }

  /**
   * Locates every entity value the filter already extracted — person, collaborators, genre, and
   * either end of a year range — back in the original text.
   *
   * @param text the query text
   * @param filter the extracted filter
   * @return one ENTITY span per value actually found in {@code text}; a value the model extracted
   *     but that (for whatever reason) doesn't literally appear in the text is silently skipped
   *     rather than guessed at
   */
  private static List<QuerySpanDto> entitySpans(String text, StructuredQueryFilterDto filter) {
    List<QuerySpanDto> spans = new ArrayList<>();
    addEntitySpan(spans, text, filter.personName());
    for (String collaborator : filter.collaborators()) {
      addEntitySpan(spans, text, collaborator);
    }
    addEntitySpan(spans, text, filter.genre());
    addEntitySpan(
        spans, text, filter.yearFrom() == null ? null : String.valueOf(filter.yearFrom()));
    addEntitySpan(spans, text, filter.yearTo() == null ? null : String.valueOf(filter.yearTo()));
    return spans;
  }

  /**
   * Appends an ENTITY span for {@code needle} to {@code spans} if it's found in {@code text}.
   *
   * @param spans the accumulator to append to
   * @param text the query text
   * @param needle the value to find, possibly {@code null}
   */
  private static void addEntitySpan(List<QuerySpanDto> spans, String text, String needle) {
    int[] span = findWordSpan(text, needle);
    if (span != null) {
      spans.add(
          new QuerySpanDto(
              text.substring(span[0], span[1]), QuerySpanCategory.ENTITY, span[0], span[1]));
    }
  }

  /**
   * Locates {@code needle} in {@code text} as a whole word/phrase, matched case-insensitively but
   * returning the position and original casing of the actual occurrence. Boundaries are Unicode-
   * aware ({@link Pattern#UNICODE_CHARACTER_CLASS}) so an accented letter at either edge of {@code
   * needle} (e.g. a name like "François") counts as a word character rather than tripping a false
   * boundary — #207 AC2's non-ASCII accuracy requirement.
   *
   * @param text the text to search
   * @param needle the literal value to find, possibly {@code null} or blank
   * @return the match's {@code [start, end)} offsets, or {@code null} if {@code needle} is
   *     null/blank or not found in {@code text}
   */
  private static int[] findWordSpan(String text, String needle) {
    if (needle == null || needle.isBlank()) {
      return null;
    }
    Pattern pattern =
        Pattern.compile(
            "\\b" + Pattern.quote(needle) + "\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? new int[] {matcher.start(), matcher.end()} : null;
  }

  /**
   * Extends a negation cue's end offset to include the immediately-following word, if any — see
   * {@link #negationSpan}'s Javadoc for why ("not" → "not starring"). Does not extend into a word
   * that is itself the start of an already-recognized ENTITY span (see {@link #negationSpan}'s
   * {@code entityStarts} parameter) — that word gets its own ENTITY span instead, rather than being
   * absorbed into this one too.
   *
   * @param text the query text
   * @param cueEnd the negation cue's own end offset
   * @param entityStarts start offsets of every already-computed ENTITY span
   * @return {@code cueEnd} extended past one run of whitespace then one run of letters, or
   *     unchanged if no such word immediately follows, or if that word is an ENTITY span's start
   */
  private static int extendToFollowingWord(String text, int cueEnd, Set<Integer> entityStarts) {
    int i = cueEnd;
    while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
      i++;
    }
    int wordStart = i;
    if (entityStarts.contains(wordStart)) {
      return cueEnd;
    }
    while (i < text.length() && Character.isLetter(text.charAt(i))) {
      i++;
    }
    return i > wordStart ? i : cueEnd;
  }
}
