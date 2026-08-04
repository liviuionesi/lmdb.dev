// Tests ReviewMediaSection: submission validation, successful file upload,
// unauthenticated prompts, thumbnail rendering, and interactive lightbox modal display.
import React from 'react';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { configureStore } from '@reduxjs/toolkit';

import ReviewMediaSection from './ReviewMediaSection';
import authReducer from '../../features/auth';
import { renderWithProviders } from '../../test-utils/render';
import { useGetMediaForEntityQuery, useUploadMediaMutation, getMediaUrl } from '../../services/media';

jest.mock('../../services/media', () => ({
  useGetMediaForEntityQuery: jest.fn(),
  useUploadMediaMutation: jest.fn(),
  getMediaUrl: jest.fn(),
}));

const buildStore = (authenticated = true) => configureStore({
  reducer: { user: authReducer },
  preloadedState: { user: { user: authenticated ? { id: 1, username: 'liviu' } : null, isAuthenticated: authenticated } },
});

describe('ReviewMediaSection', () => {
  let mockUpload;

  beforeEach(() => {
    mockUpload = jest.fn().mockReturnValue({ unwrap: jest.fn().mockResolvedValue({}) });
    useGetMediaForEntityQuery.mockReturnValue({ data: [], refetch: jest.fn(), isFetching: false });
    useUploadMediaMutation.mockReturnValue([mockUpload, { isLoading: false }]);
    getMediaUrl.mockImplementation((url) => url);
    jest.clearAllMocks();
  });

  it('renders login notification when user is unauthenticated', () => {
    renderWithProviders(<ReviewMediaSection movieId={550} />, { store: buildStore(false) });
    expect(screen.getByText(/Please login to upload your own screenshot/i)).toBeInTheDocument();
  });

  it('renders review form and empty gallery state when authenticated', () => {
    renderWithProviders(<ReviewMediaSection movieId={550} />, { store: buildStore(true) });
    expect(screen.getByText(/Fan Reviews & Proof Gallery/i)).toBeInTheDocument();
    expect(screen.getByText(/No review attachments uploaded for this movie yet/i)).toBeInTheDocument();
  });

  it('displays validation error when attempting to attach an unsupported file format', async () => {
    renderWithProviders(<ReviewMediaSection movieId={550} />, { store: buildStore(true) });

    const input = screen.getByTestId('review-attachment-input');
    const invalidFile = new File(['text content'], 'document.pdf', { type: 'application/pdf' });

    fireEvent.change(input, { target: { files: [invalidFile] } });

    expect(await screen.findByText(/Only image \(JPG\/PNG\) and video \(MP4\/MOV\/WEBM\) formats/)).toBeInTheDocument();
  });

  it('displays validation error when file exceeds 20MB limit', async () => {
    renderWithProviders(<ReviewMediaSection movieId={550} />, { store: buildStore(true) });

    const input = screen.getByTestId('review-attachment-input');
    const largeFile = new File([''], 'huge-movie.mp4', { type: 'video/mp4' });
    Object.defineProperty(largeFile, 'size', { value: 25 * 1024 * 1024 });

    fireEvent.change(input, { target: { files: [largeFile] } });

    expect(await screen.findByText(/Attachment file size exceeds the 20MB maximum/)).toBeInTheDocument();
  });

  it('successfully uploads media attachment with entityType=MOVIE_REVIEW upon form submission', async () => {
    renderWithProviders(<ReviewMediaSection movieId={550} />, { store: buildStore(true) });

    const input = screen.getByTestId('review-attachment-input');
    const validImage = new File(['proof'], 'screenshot.png', { type: 'image/png' });
    Object.defineProperty(validImage, 'size', { value: 1 * 1024 * 1024 });

    fireEvent.change(input, { target: { files: [validImage] } });
    expect(screen.getByText(/Attached: screenshot.png/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /submit review/i }));

    await waitFor(() => expect(mockUpload).toHaveBeenCalledWith(expect.objectContaining({
      file: validImage,
      entityId: '550',
      entityType: 'MOVIE_REVIEW',
      mediaType: 'IMAGE',
      uploadedBy: 'liviu',
    })));

    expect(await screen.findByText('Review submitted successfully!')).toBeInTheDocument();
  });

  it('renders thumbnail gallery and opens interactive lightbox modal upon click', async () => {
    useGetMediaForEntityQuery.mockReturnValue({
      data: [
        { id: 'm1', mediaType: 'IMAGE', thumbnails: { medium: '/med.jpg', original: '/orig.jpg' }, uploadedBy: 'bob' },
        { id: 'm2', mediaType: 'VIDEO', mimeType: 'video/mp4', thumbnails: { original: '/clip.mp4' }, uploadedBy: 'alice' },
      ],
      refetch: jest.fn(),
      isFetching: false,
    });

    renderWithProviders(<ReviewMediaSection movieId={550} />, { store: buildStore(true) });

    expect(screen.getByText(/By @bob/)).toBeInTheDocument();
    expect(screen.getByText(/By @alice/)).toBeInTheDocument();

    // Click image gallery item to open lightbox
    const item1 = screen.getByTestId('gallery-item-m1');
    await userEvent.click(item1);

    expect(screen.getByTestId('lightbox-image')).toHaveAttribute('src', '/orig.jpg');

    // Close modal
    await userEvent.click(screen.getByLabelText('close-lightbox'));

    // Click video gallery item to verify video tag renderer
    const item2 = screen.getByTestId('gallery-item-m2');
    await userEvent.click(item2);

    expect(screen.getByTestId('lightbox-video')).toHaveAttribute('src', '/clip.mp4');
  });
});
