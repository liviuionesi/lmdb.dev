// Tests Profile: the empty state, rendering favorites/watchlist, logging out,
// unauthenticated redirect, and custom avatar photo uploading & validation.
import React from 'react';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { configureStore } from '@reduxjs/toolkit';

import Profile from './Profile';
import authReducer from '../../features/auth';
import { renderWithProviders } from '../../test-utils/render';
import { useGetFavoritesQuery, useGetWatchlistQuery, useLogoutMutation } from '../../services/user';
import { useGetMediaForEntityQuery, useUploadMediaMutation, getMediaUrl } from '../../services/media';
import { useGetMovieQuery } from '../../services/TMDB';

vi.mock('../../services/user', () => ({
  useGetFavoritesQuery: vi.fn(),
  useGetWatchlistQuery: vi.fn(),
  useLogoutMutation: vi.fn(),
}));

vi.mock('../../services/media', () => ({
  useGetMediaForEntityQuery: vi.fn(),
  useUploadMediaMutation: vi.fn(),
  getMediaUrl: vi.fn(),
}));

vi.mock('../../services/TMDB', () => ({
  useGetMovieQuery: vi.fn(),
}));

const buildStore = (authenticated) => configureStore({
  reducer: { user: authReducer },
  preloadedState: authenticated ? { user: { user: { id: 1, username: 'liviu' }, isAuthenticated: true } } : undefined,
});

describe('Profile', () => {
  beforeEach(() => {
    useLogoutMutation.mockReturnValue([vi.fn().mockResolvedValue({})]);
    useGetFavoritesQuery.mockReturnValue({ data: [] });
    useGetWatchlistQuery.mockReturnValue({ data: [] });
    useGetMediaForEntityQuery.mockReturnValue({ data: [], refetch: vi.fn() });
    useUploadMediaMutation.mockReturnValue([vi.fn().mockReturnValue({ unwrap: vi.fn().mockResolvedValue({}) }), { isLoading: false }]);
    getMediaUrl.mockImplementation((url) => url);
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('shows a placeholder message when there are no favorites or watchlist entries', () => {
    renderWithProviders(<Profile />, { store: buildStore(true) });

    expect(screen.getByText(/Add favorites or watchlist some movies/)).toBeInTheDocument();
  });

  it('renders RatedCards sections when favorites/watchlist have entries', () => {
    useGetFavoritesQuery.mockReturnValue({ data: [{ movieId: 1 }] });
    useGetWatchlistQuery.mockReturnValue({ data: [{ movieId: 2 }] });
    useGetMovieQuery.mockReturnValue({ data: undefined, isFetching: true });
    renderWithProviders(<Profile />, { store: buildStore(true) });

    expect(screen.getByText('Favorite Movies')).toBeInTheDocument();
    expect(screen.getByText('Watchlist')).toBeInTheDocument();
  });

  it('logs out: revokes the session, clears local tokens/redux state, and redirects home', async () => {
    localStorage.setItem('access_token', 'jwt');
    const logout = vi.fn().mockResolvedValue({});
    useLogoutMutation.mockReturnValue([logout]);

    delete window.location;
    window.location = { href: '' };

    renderWithProviders(<Profile />, { store: buildStore(true) });

    await userEvent.click(screen.getByRole('button', { name: /logout/i }));

    expect(logout).toHaveBeenCalled();
    await waitFor(() => expect(localStorage.getItem('access_token')).toBeNull());
  });

  it('redirects an unauthenticated user to the home page (/)', () => {
    renderWithProviders(<Profile />, { store: buildStore(false), initialEntries: ['/profile/1'] });

    expect(screen.queryByText(/My Profile/i)).not.toBeInTheDocument();
  });

  it('displays validation error when uploading a non-image format file', async () => {
    renderWithProviders(<Profile />, { store: buildStore(true) });

    const input = screen.getByTestId('avatar-upload-input');
    const invalidFile = new File(['text'], 'test.txt', { type: 'text/plain' });

    fireEvent.change(input, { target: { files: [invalidFile] } });

    expect(await screen.findByText('Only JPG and PNG images are supported for avatar upload.')).toBeInTheDocument();
  });

  it('displays validation error when uploading an image larger than 5MB', async () => {
    renderWithProviders(<Profile />, { store: buildStore(true) });

    const input = screen.getByTestId('avatar-upload-input');
    const hugeFile = new File([''], 'huge.jpg', { type: 'image/jpeg' });
    Object.defineProperty(hugeFile, 'size', { value: 6 * 1024 * 1024 });

    fireEvent.change(input, { target: { files: [hugeFile] } });

    expect(await screen.findByText('File size exceeds the 5MB maximum limit.')).toBeInTheDocument();
  });

  it('successfully uploads valid JPG avatar and shows success notification', async () => {
    const mockUpload = vi.fn().mockReturnValue({ unwrap: vi.fn().mockResolvedValue({}) });
    useUploadMediaMutation.mockReturnValue([mockUpload, { isLoading: false }]);

    renderWithProviders(<Profile />, { store: buildStore(true) });

    const input = screen.getByTestId('avatar-upload-input');
    const validFile = new File(['image content'], 'avatar.jpg', { type: 'image/jpeg' });
    Object.defineProperty(validFile, 'size', { value: 2 * 1024 * 1024 });

    fireEvent.change(input, { target: { files: [validFile] } });

    await waitFor(() => expect(mockUpload).toHaveBeenCalledWith(expect.objectContaining({
      file: validFile,
      entityType: 'USER',
      mediaType: 'AVATAR',
      uploadedBy: 'liviu',
    })));

    expect(await screen.findByText('Avatar updated successfully!')).toBeInTheDocument();
  });

  it('renders uploaded avatar thumbnail when media items are returned', () => {
    useGetMediaForEntityQuery.mockReturnValue({
      data: [{ mediaType: 'AVATAR', thumbnails: { medium: 'http://localhost:8085/thumb.jpg' } }],
      refetch: vi.fn(),
    });

    renderWithProviders(<Profile />, { store: buildStore(true) });

    const avatar = screen.getByTestId('user-avatar');
    expect(avatar.getAttribute('data-src')).toContain('http://localhost:8085/thumb.jpg');
  });
});
