// Tests App's own wiring, not BackendStandbyModal's behavior (that's
// BackendStandbyModal.test.jsx's job): that App actually passes
// `onBackendReady={() => dispatch(tmdbApi.util.resetApiState())}` to
// BackendStandbyModal, so every cached RTK Query result is dropped and
// re-fetched once the backend comes back from standby.
//
// App renders NavBar, Movies, Footer, VoiceControl and ChatWidget too, each
// pulling in its own RTK Query hooks (and, for VoiceControl, real browser
// media APIs) that have nothing to do with this wiring. Rather than mock
// every one of those services just to get App to render, the sibling
// components barrel and VoiceControl are stubbed out directly so the only
// real thing under test is App's own handleBackendReady wiring.
import React from 'react';
import { screen, fireEvent } from '@testing-library/react';
import { configureStore } from '@reduxjs/toolkit';

import App from './App';
import { tmdbApi } from '../services/TMDB';
import { renderWithProviders } from '../test-utils/render';

vi.mock('.', () => ({
  About: () => null,
  Actors: () => null,
  AdminDashboard: () => null,
  BackendStandbyModal: ({ onBackendReady }) => (
    <button type="button" data-testid="trigger-backend-ready" onClick={onBackendReady}>
      trigger backend ready
    </button>
  ),
  ChatWidget: () => null,
  Footer: () => null,
  MovieInformation: () => null,
  Movies: () => null,
  NavBar: () => null,
  Profile: () => null,
  Recommendations: () => null,
}));

vi.mock('./VoiceControl/VoiceControl', () => ({ default: () => null }));

const buildStore = () => configureStore({
  reducer: { [tmdbApi.reducerPath]: tmdbApi.reducer },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(tmdbApi.middleware),
});

describe('App backend-ready wiring', () => {
  it('dispatches tmdbApi.util.resetApiState() when BackendStandbyModal reports the backend ready', () => {
    const store = buildStore();
    const dispatchSpy = vi.spyOn(store, 'dispatch');

    renderWithProviders(<App />, { store });

    fireEvent.click(screen.getByTestId('trigger-backend-ready'));

    expect(dispatchSpy).toHaveBeenCalledWith(tmdbApi.util.resetApiState());
  });
});
