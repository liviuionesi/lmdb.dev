// Tests HighlightLegend (#209 AC3): a focusable info affordance whose Tooltip explains what each
// highlight style means, without requiring external documentation.
import React from 'react';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import HighlightLegend, { HIGHLIGHT_LEGEND_TEXT } from './HighlightLegend';
import { renderWithProviders } from '../../test-utils/render';

describe('HighlightLegend', () => {
  it('renders a focusable button carrying the legend text as its accessible name', () => {
    renderWithProviders(<HighlightLegend />);
    expect(screen.getByRole('button', { name: HIGHLIGHT_LEGEND_TEXT })).toBeInTheDocument();
  });

  it('shows the legend text in a tooltip on keyboard focus, not just hover, so it is reachable without a mouse', async () => {
    renderWithProviders(<HighlightLegend />);
    const button = screen.getByRole('button', { name: HIGHLIGHT_LEGEND_TEXT });

    await userEvent.tab(); // moves focus to the (only) focusable element: the legend button
    expect(button).toHaveFocus();

    expect(await screen.findByText(HIGHLIGHT_LEGEND_TEXT)).toBeInTheDocument();
  });
});
