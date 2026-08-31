// Tests QueryHighlightOverlay (#209): the pure buildSegments span-to-segment mapping, and the
// component's own rendering of those segments (category classNames applied, aria-hidden, ref
// forwarding for Search.jsx's scrollLeft sync).
import React, { createRef } from 'react';
import { render } from '@testing-library/react';

import QueryHighlightOverlay, { buildSegments } from './QueryHighlightOverlay';
import { HIGHLIGHT_LEGEND_TEXT } from './HighlightLegend';

describe('buildSegments', () => {
  it('returns no segments for an empty query', () => {
    expect(buildSegments('', [])).toEqual([]);
  });

  it('returns one plain segment covering the whole query when there are no spans', () => {
    expect(buildSegments('batman', [])).toEqual([{ text: 'batman', category: null }]);
  });

  it('treats a null/undefined spans list the same as an empty one', () => {
    expect(buildSegments('batman', undefined)).toEqual([{ text: 'batman', category: null }]);
  });

  it('fills the gap before, between, and after spans with plain segments', () => {
    // "a and b" — "and" (2-5) is the only span; "a " and " b" are plain gaps.
    const spans = [{ text: 'and', category: 'CONNECTOR', start: 2, end: 5 }];
    expect(buildSegments('a and b', spans)).toEqual([
      { text: 'a ', category: null },
      { text: 'and', category: 'CONNECTOR' },
      { text: ' b', category: null },
    ]);
  });

  it('produces no plain segment when a single span covers the entire query', () => {
    const spans = [{ text: 'inception', category: 'ENTITY', start: 0, end: 9 }];
    expect(buildSegments('inception', spans)).toEqual([{ text: 'inception', category: 'ENTITY' }]);
  });

  it('handles multiple, non-adjacent spans of different categories in one query', () => {
    // "Tom Hanks and not Meg Ryan" — ENTITY "Tom Hanks" (0-9), CONNECTOR "and" (10-13),
    // NEGATION "not" (14-17), ENTITY "Meg Ryan" (18-26).
    const spans = [
      {
        text: 'Tom Hanks', category: 'ENTITY', start: 0, end: 9,
      },
      {
        text: 'and', category: 'CONNECTOR', start: 10, end: 13,
      },
      {
        text: 'not', category: 'NEGATION', start: 14, end: 17,
      },
      {
        text: 'Meg Ryan', category: 'ENTITY', start: 18, end: 26,
      },
    ];
    expect(buildSegments('Tom Hanks and not Meg Ryan', spans)).toEqual([
      { text: 'Tom Hanks', category: 'ENTITY' },
      { text: ' ', category: null },
      { text: 'and', category: 'CONNECTOR' },
      { text: ' ', category: null },
      { text: 'not', category: 'NEGATION' },
      { text: ' ', category: null },
      { text: 'Meg Ryan', category: 'ENTITY' },
    ]);
  });

  it('sorts out-of-order spans by start offset before building segments', () => {
    const spans = [
      { text: 'b', category: 'ENTITY', start: 2, end: 3 },
      { text: 'a', category: 'CONNECTOR', start: 0, end: 1 },
    ];
    expect(buildSegments('a b', spans)).toEqual([
      { text: 'a', category: 'CONNECTOR' },
      { text: ' ', category: null },
      { text: 'b', category: 'ENTITY' },
    ]);
  });

  it('drops a span that overlaps an earlier one rather than double-rendering the text', () => {
    const spans = [
      { text: 'batman', category: 'ENTITY', start: 0, end: 6 },
      { text: 'man', category: 'ENTITY', start: 3, end: 6 }, // overlaps the span above
    ];
    expect(buildSegments('batman', spans)).toEqual([{ text: 'batman', category: 'ENTITY' }]);
  });

  it('ignores a span whose offsets fall outside the current query (stale relative to an edit)', () => {
    // A span computed for a longer, earlier query text is now out of range for the shorter one.
    const spans = [{ text: 'x', category: 'ENTITY', start: 0, end: 20 }];
    expect(buildSegments('batman', spans)).toEqual([{ text: 'batman', category: null }]);
  });

  it('ignores a zero-width or inverted span rather than crashing on an empty slice', () => {
    const spans = [{ text: '', category: 'ENTITY', start: 3, end: 3 }];
    expect(buildSegments('batman', spans)).toEqual([{ text: 'batman', category: null }]);
  });
});

describe('QueryHighlightOverlay', () => {
  it('renders every segment of the query in order, as plain text when there are no spans', () => {
    const { container } = render(<QueryHighlightOverlay query="batman" spans={[]} />);
    expect(container).toHaveTextContent('batman');
  });

  // A same-class-count check would pass even if all three categories rendered visually identical
  // CSS (each style-object key still compiles to its own class name regardless of what's inside
  // it) — asserting the actual computed text-decoration/weight is what makes this able to catch
  // AC1 ("not color alone") actually regressing, e.g. all three collapsing to the same underline.
  it('renders CONNECTOR with a dotted underline, distinguishable by more than color (AC1)', () => {
    const spans = [{ text: 'and', category: 'CONNECTOR', start: 0, end: 3 }];
    const { container } = render(<QueryHighlightOverlay query="and" spans={spans} />);
    const style = getComputedStyle(container.querySelector('span'));
    expect(style.textDecorationLine).toBe('underline');
    expect(style.textDecorationStyle).toBe('dotted');
    expect(HIGHLIGHT_LEGEND_TEXT).toContain('dotted underline = connector');
  });

  it('renders NEGATION with a bold, wavy underline, distinguishable by more than color (AC1)', () => {
    const spans = [{ text: 'not', category: 'NEGATION', start: 0, end: 3 }];
    const { container } = render(<QueryHighlightOverlay query="not" spans={spans} />);
    const style = getComputedStyle(container.querySelector('span'));
    expect(style.textDecorationLine).toBe('underline');
    expect(style.textDecorationStyle).toBe('wavy');
    expect(style.fontWeight).toBe('700');
    expect(HIGHLIGHT_LEGEND_TEXT).toContain('bold wavy underline = negation');
  });

  it('renders ENTITY with a solid underline, distinguishable by more than color (AC1)', () => {
    const spans = [{ text: 'Batman', category: 'ENTITY', start: 0, end: 6 }];
    const { container } = render(<QueryHighlightOverlay query="Batman" spans={spans} />);
    const style = getComputedStyle(container.querySelector('span'));
    expect(style.textDecorationLine).toBe('underline');
    expect(style.textDecorationStyle).toBe('solid');
    expect(HIGHLIGHT_LEGEND_TEXT).toContain('solid underline = recognized entity');
  });

  it('renders three categories in one query with three distinct underline styles, not just three distinct class names', () => {
    const spans = [
      { text: 'and', category: 'CONNECTOR', start: 2, end: 5 },
      { text: 'not', category: 'NEGATION', start: 6, end: 9 },
      {
        text: 'Batman', category: 'ENTITY', start: 10, end: 16,
      },
    ];
    const { container } = render(<QueryHighlightOverlay query="a and not Batman" spans={spans} />);
    const highlighted = [...container.querySelectorAll('span')].filter((el) => el.textContent !== 'a ' && el.textContent !== ' ');
    const decorationStyles = highlighted.map((el) => getComputedStyle(el).textDecorationStyle);
    expect(new Set(decorationStyles)).toEqual(new Set(['dotted', 'wavy', 'solid']));
  });

  it('is aria-hidden — the real, focusable <input> underneath owns the accessible value', () => {
    const { container } = render(<QueryHighlightOverlay query="batman" spans={[]} />);
    expect(container.firstChild).toHaveAttribute('aria-hidden', 'true');
  });

  it('forwards the ref to its root node, so a scrollLeft sync can target it', () => {
    const ref = createRef();
    render(<QueryHighlightOverlay ref={ref} query="batman" spans={[]} />);
    expect(ref.current).toBeInstanceOf(HTMLElement);
    expect(ref.current).toHaveTextContent('batman');
  });

  it('applies the geometry passed via `style`, for positioning over the real input', () => {
    const { container } = render(
      <QueryHighlightOverlay query="batman" spans={[]} style={{ left: 10, top: 2, width: 100, height: 20 }} />,
    );
    expect(container.firstChild).toHaveStyle({
      left: '10px', top: '2px', width: '100px', height: '20px',
    });
  });
});
