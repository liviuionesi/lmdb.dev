import React from 'react';
import { IconButton, Tooltip } from '@mui/material';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';

import { HIGHLIGHT_CATEGORIES } from './highlightCategories';

// #209 AC3: a short legend explaining what each highlight style means, discoverable without
// external documentation. Built from the same HIGHLIGHT_CATEGORIES list QueryHighlightOverlay
// picks its CSS classes from, rather than a hand-written copy of the same three descriptions, so
// this text can't silently drift out of sync with what the overlay actually renders. Plain text
// (no color swatches) so the same explanation reaches a screen reader via `aria-label`, matching
// the Tooltip's own title exactly.
export const HIGHLIGHT_LEGEND_TEXT = `Search highlights: ${HIGHLIGHT_CATEGORIES.map((c) => c.description).join(', ')}.`;

/**
 * Small info affordance next to the search field, shown only while query highlighting is active
 * (Search.jsx renders this in the TextField's endAdornment). An `IconButton` rather than a bare
 * icon so it's a real focusable control — MUI's `Tooltip` shows on keyboard focus as well as hover,
 * so the legend is reachable without a mouse, same as everywhere else `Tooltip` is used in this app
 * (e.g. `Movie.jsx`'s rating tooltip).
 */
function HighlightLegend() {
  return (
    <Tooltip title={HIGHLIGHT_LEGEND_TEXT}>
      <IconButton size="small" edge="end" aria-label={HIGHLIGHT_LEGEND_TEXT}>
        <InfoOutlinedIcon fontSize="small" />
      </IconButton>
    </Tooltip>
  );
}

export default HighlightLegend;
