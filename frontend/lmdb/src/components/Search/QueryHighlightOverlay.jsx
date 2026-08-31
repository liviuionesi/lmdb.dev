import React, { forwardRef } from 'react';

import useHighlightStyles from './highlightStyles';
import { CATEGORY_CLASS } from './highlightCategories';

/**
 * Splits `query` into an ordered list of renderable segments from ai-service's span breakdown
 * (#207's `QuerySpanDto`: `{text, category, start, end}`, UTF-16 code-unit offsets — identical to
 * JavaScript string indexing, per that DTO's Javadoc, so no re-encoding is needed here). Every
 * character of `query` ends up in exactly one segment, in original order: gaps before, between, or
 * after spans become plain (`category: null`) segments so highlighting can never drop text.
 *
 * @param {string} query the current search-field value the spans were parsed against
 * @param {Array<{text: string, category: string, start: number, end: number}>} spans the
 *   category/offset breakdown to render as highlights (possibly stale relative to `query` — see
 *   below)
 * @returns {Array<{text: string, category: string|null}>} ordered segments covering the whole
 *   query
 */
export const buildSegments = (query, spans) => {
  if (!query) return [];

  // Search.jsx's own latestParseRef guard (#208 AC2) already discards a debounced response that
  // arrives after the query has moved on, but defends here too: an out-of-range or overlapping
  // span (stale relative to a same-length edit, or simply malformed) must not corrupt rendering —
  // clip and sort first, then skip anything that would overlap the span before it.
  const validSpans = (spans ?? [])
    .filter((span) => span.start >= 0 && span.end <= query.length && span.start < span.end)
    .sort((a, b) => a.start - b.start);

  const segments = [];
  let cursor = 0;
  validSpans.forEach((span) => {
    if (span.start < cursor) return; // overlaps the previous span — keep the earlier one
    if (span.start > cursor) segments.push({ text: query.slice(cursor, span.start), category: null });
    segments.push({ text: query.slice(span.start, span.end), category: span.category });
    cursor = span.end;
  });
  if (cursor < query.length) segments.push({ text: query.slice(cursor), category: null });

  return segments;
};

/**
 * Read-only rendition of the search query with #207's span breakdown highlighted (#209) — laid
 * over the real `<input>` Search.jsx renders underneath (its text made transparent, inline, while
 * this is showing), since neither a native `<input>` nor MUI's `TextField` can style a substring
 * directly (see #209's Notes: this is the overlay approach it calls for, as opposed to a
 * contenteditable rewrite of the field). Search.jsx owns positioning this exactly over that real
 * input — including keeping the forwarded ref's `scrollLeft` synced to the input's own as the user
 * types past the field's visible width (#209 AC2) — and supplies the measured geometry via `style`.
 *
 * `aria-hidden`: this is a pure visual duplicate of the real input's value; screen readers already
 * get the correct accessible value from the actual, focusable `<input>` underneath, which keeps
 * receiving every keystroke, paste, and selection normally.
 *
 * @param {string} query the current search-field value
 * @param {Array} spans the span breakdown to highlight, straight from Redux's `queryHighlightSpans`
 */
const QueryHighlightOverlay = forwardRef(({ query, spans, style }, ref) => {
  const { classes, cx } = useHighlightStyles();
  const segments = buildSegments(query, spans);

  return (
    <div ref={ref} className={classes.overlay} style={style} aria-hidden="true" data-testid="query-highlight-overlay">
      {segments.map((segment, index) => (
        <span
          key={index}
          className={segment.category
            ? cx(classes.segment, classes[CATEGORY_CLASS[segment.category]])
            : classes.segment}
        >
          {segment.text}
        </span>
      ))}
    </div>
  );
});

QueryHighlightOverlay.displayName = 'QueryHighlightOverlay';

export default QueryHighlightOverlay;
