package dev.lmdb.ai.dto;

/**
 * One highlighted span within a natural-language query's original text — a token or phrase {@link
 * dev.lmdb.ai.service.QuerySpanExtractor} identified as a connector, negation, or entity, so the
 * frontend can render live highlighting as the user types without running its own NLP (#207,
 * ADR-020; consumed by Story #199's #209).
 *
 * <p>{@code start}/{@code end} are UTF-16 code-unit offsets into the exact text {@link
 * dev.lmdb.ai.service.QueryParsingService#parseWithSpans} extracted {@code text.category} spans
 * from — not byte offsets. JavaScript strings are themselves UTF-16 code-unit indexed, so the
 * frontend can slice its own search-field value directly by these offsets with no re-encoding,
 * including for non-ASCII input such as accented names (#207 AC2).
 *
 * @param text the exact substring of the original query this span covers
 * @param category what kind of span this is
 * @param start inclusive start offset into the original query text
 * @param end exclusive end offset into the original query text
 */
public record QuerySpanDto(String text, QuerySpanCategory category, int start, int end) {}
