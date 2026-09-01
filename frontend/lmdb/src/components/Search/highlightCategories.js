// Single source of truth for #209's three highlight categories, mirroring #207's
// QuerySpanCategory enum exactly (`CONNECTOR` | `NEGATION` | `ENTITY`). QueryHighlightOverlay uses
// `className` to pick each category's CSS (highlightStyles.js), and HighlightLegend builds its
// legend text from `description` — keeping both derived from this one list is what lets a test
// actually catch the two drifting apart (e.g. a CSS change here with no matching legend-text
// update), rather than each file hand-maintaining its own copy of "what dotted/wavy/solid means".
export const HIGHLIGHT_CATEGORIES = [
  { category: 'CONNECTOR', className: 'connector', description: 'dotted underline = connector (and/or)' },
  { category: 'NEGATION', className: 'negation', description: 'bold wavy underline = negation' },
  { category: 'ENTITY', className: 'entity', description: 'solid underline = recognized entity (person, year, genre)' },
];

// Maps a QuerySpanDto's `category` string to its highlightStyles.js class name.
export const CATEGORY_CLASS = Object.fromEntries(
  HIGHLIGHT_CATEGORIES.map(({ category, className }) => [category, className]),
);
