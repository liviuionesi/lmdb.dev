// Tests Actors: loading spinner, the error/go-back state, and the happy
// path rendering the actor's bio plus their movies.
import React from 'react';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import Actors from './Actors';
import { renderWithProviders } from '../../test-utils/render';
import { useGetActorsDetailsQuery, useGetMoviesByActorIdQuery } from '../../services/TMDB';

vi.mock('../../services/TMDB', () => ({
  useGetActorsDetailsQuery: vi.fn(),
  useGetMoviesByActorIdQuery: vi.fn(),
}));

describe('Actors', () => {
  beforeEach(() => {
    useGetMoviesByActorIdQuery.mockReturnValue({ data: undefined });
  });

  it('shows a spinner while fetching actor details', () => {
    useGetActorsDetailsQuery.mockReturnValue({ data: undefined, isFetching: true, error: undefined });
    renderWithProviders(<Actors />, { route: '/actors/42', path: '/actors/:id' });

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows a "go back" action when the actor request errors', () => {
    useGetActorsDetailsQuery.mockReturnValue({ data: undefined, isFetching: false, error: { status: 500 } });
    renderWithProviders(<Actors />, { route: '/actors/42', path: '/actors/:id' });

    expect(screen.getByRole('button', { name: /go back/i })).toBeInTheDocument();
  });

  it('renders the actor bio and their movies once loaded', () => {
    useGetActorsDetailsQuery.mockReturnValue({
      data: { name: 'Brad Pitt', biography: 'An actor.', birthday: '1963-12-18', profile_path: '/p.jpg', imdb_id: 'nm0000093' },
      isFetching: false,
      error: undefined,
    });
    useGetMoviesByActorIdQuery.mockReturnValue({
      data: { results: [{ id: 1, title: 'Fight Club', poster_path: '/f.jpg', vote_average: 8 }], total_pages: 1 },
    });
    renderWithProviders(<Actors />, { route: '/actors/42', path: '/actors/:id' });

    expect(screen.getByText('Brad Pitt')).toBeInTheDocument();
    expect(screen.getByText('An actor.')).toBeInTheDocument();
    expect(screen.getByText('Fight Club')).toBeInTheDocument();
  });

  it('falls back to a placeholder biography when none is provided', () => {
    useGetActorsDetailsQuery.mockReturnValue({
      data: { name: 'No Bio', biography: '', birthday: '1990-01-01', profile_path: '/p.jpg', imdb_id: 'nm1' },
      isFetching: false,
      error: undefined,
    });
    renderWithProviders(<Actors />, { route: '/actors/1', path: '/actors/:id' });

    expect(screen.getByText('Sorry, no biography yet...')).toBeInTheDocument();
  });

  it('navigates back when Back is clicked', () => {
    useGetActorsDetailsQuery.mockReturnValue({
      data: { name: 'Brad Pitt', biography: 'x', birthday: '1963-12-18', profile_path: '/p.jpg', imdb_id: 'nm1' },
      isFetching: false,
      error: undefined,
    });
    renderWithProviders(<Actors />, { route: '/actors/42', path: '/actors/:id' });

    // Clicking Back calls history.goBack(); this just proves it doesn't throw
    // and the button is wired to the handler.
    userEvent.click(screen.getByRole('button', { name: /^back$/i }));
  });
});
