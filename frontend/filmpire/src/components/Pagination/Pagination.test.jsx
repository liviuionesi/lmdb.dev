// Tests Pagination's prev/next button gating at the first/last page, and
// that it renders nothing once there are no pages to show.
import React from 'react';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import Pagination from './Pagination';
import { renderWithProviders } from '../../test-utils/render';

describe('Pagination', () => {
  it('renders the current page number', () => {
    renderWithProviders(<Pagination currentPage={3} totalPages={10} setPage={() => {}} />);
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('calls setPage with the previous page when Prev is clicked', () => {
    const setPage = vi.fn();
    renderWithProviders(<Pagination currentPage={3} totalPages={10} setPage={setPage} />);

    userEvent.click(screen.getByText('Prev'));

    expect(setPage).toHaveBeenCalledTimes(1);
    expect(setPage.mock.calls[0][0](3)).toBe(2);
  });

  it('does not call setPage when Prev is clicked on the first page', () => {
    const setPage = vi.fn();
    renderWithProviders(<Pagination currentPage={1} totalPages={10} setPage={setPage} />);

    userEvent.click(screen.getByText('Prev'));

    expect(setPage).not.toHaveBeenCalled();
  });

  it('calls setPage with the next page when Next is clicked', () => {
    const setPage = vi.fn();
    renderWithProviders(<Pagination currentPage={3} totalPages={10} setPage={setPage} />);

    userEvent.click(screen.getByText('Next'));

    expect(setPage).toHaveBeenCalledTimes(1);
    expect(setPage.mock.calls[0][0](3)).toBe(4);
  });

  it('does not call setPage when Next is clicked on the last page', () => {
    const setPage = vi.fn();
    renderWithProviders(<Pagination currentPage={10} totalPages={10} setPage={setPage} />);

    userEvent.click(screen.getByText('Next'));

    expect(setPage).not.toHaveBeenCalled();
  });

  it('renders nothing when totalPages is 0', () => {
    const { container } = renderWithProviders(<Pagination currentPage={1} totalPages={0} setPage={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });
});
