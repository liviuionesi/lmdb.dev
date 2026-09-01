package dev.lmdb.ai.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/v1/ai/search/query}: the structured filter (or plain-title
 * fallback) {@link dev.lmdb.ai.service.QueryParsingService} extracts, alongside the token/span
 * breakdown Story #199's search-bar highlighting consumes (#207). Per ADR-020, the span breakdown
 * travels "alongside this filter" — a sibling field on this response, not merged into {@link
 * StructuredQueryFilterDto} itself, which stays exactly the shape #203's aggregation step already
 * reuses.
 *
 * @param filter the extracted structured filter, or plain-title fallback
 * @param spans the highlightable spans covering connectors, negations, and entities found in the
 *     original query text; empty for a plain-title query (#207 AC3) — never {@code null}
 */
public record QueryParseResponseDto(StructuredQueryFilterDto filter, List<QuerySpanDto> spans) {}
