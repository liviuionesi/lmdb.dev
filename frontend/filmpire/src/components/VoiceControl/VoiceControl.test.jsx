// Tests VoiceControl (#68): the click-to-talk Fab that records via the
// browser mic APIs, sends the clip to ai-service for transcription, and
// dispatches the resulting voice command. encodeToWav/parseVoiceCommand are
// unit-tested in their own files, so they're mocked here to isolate
// VoiceControl's own state machine (idle -> recording -> transcribing) and
// its command-dispatch branches. MediaRecorder/getUserMedia/fetch aren't in
// jsdom, so each is stubbed at the top of the file.
import React from 'react';
import { screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { configureStore } from '@reduxjs/toolkit';
import { Provider } from 'react-redux';

import VoiceControl from './VoiceControl';
import genreOrCategoryReducer from '../../features/currentGenreOrCategory';
import authReducer from '../../features/auth';
import { renderWithProviders } from '../../test-utils/render';
import { useGetGenresQuery } from '../../services/TMDB';
import { encodeToWav } from '../../utils/wavEncoder';
import { parseVoiceCommand } from '../../utils/voiceCommands';
import { clearAuthTokens } from '../../utils';
import { ColorModeContext } from '../../utils/ToggleColorMode';

vi.mock('../../services/TMDB', () => ({ useGetGenresQuery: vi.fn() }));
vi.mock('../../utils/wavEncoder', () => ({ encodeToWav: vi.fn() }));
vi.mock('../../utils/voiceCommands', () => ({ parseVoiceCommand: vi.fn() }));
// Vitest hoists vi.mock calls above imports, so the real module is fetched
// via the `importOriginal` callback rather than a synchronous requireActual.
vi.mock('../../utils', async (importOriginal) => ({
  ...(await importOriginal()),
  clearAuthTokens: vi.fn(),
}));

const mockHistoryPush = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal()),
  useHistory: () => ({ push: mockHistoryPush }),
}));

/**
 * Minimal MediaRecorder stand-in: `start()` flips state, `stop()` fires the
 * component's own `ondataavailable`/`onstop` handlers synchronously so tests
 * can drive the record -> transcribe flow without real audio I/O.
 */
class MockMediaRecorder {
  constructor(stream) {
    this.stream = stream;
    this.mimeType = 'audio/webm';
    this.ondataavailable = null;
    this.onstop = null;
  }

  start() {
    this.state = 'recording';
  }

  stop() {
    this.ondataavailable?.({ data: new Blob(['chunk']) });
    this.onstop?.();
  }
}

const buildStore = () => configureStore({
  reducer: { currentGenreOrCategory: genreOrCategoryReducer, user: authReducer },
});

const renderVoiceControl = (setMode = vi.fn()) => {
  const store = buildStore();
  const dispatchSpy = vi.spyOn(store, 'dispatch');
  renderWithProviders(
    <Provider store={store}>
      <ColorModeContext.Provider value={{ setMode }}>
        <VoiceControl />
      </ColorModeContext.Provider>
    </Provider>,
  );
  return { store, dispatchSpy };
};

// The Fab's accessible name changes with status; a feedback Snackbar adds its
// own "Close" button once shown, so tests must target the Fab specifically
// rather than assume it's the only button on the page.
const getFab = () => screen.getByRole('button', { name: /voice control|click to stop recording/i });

/** Clicks the Fab to start recording, then clicks it again to stop and let the mocked transcription flow run. */
const recordAndStop = async () => {
  await userEvent.click(getFab());
  await waitFor(() => expect(getFab()).not.toBeDisabled());
  await userEvent.click(getFab());
};

describe('VoiceControl', () => {
  let getUserMedia;

  beforeEach(() => {
    vi.clearAllMocks();
    useGetGenresQuery.mockReturnValue({
      data: { genres: [{ id: 28, name: 'Action' }, { id: 35, name: 'Comedy' }] },
    });
    encodeToWav.mockResolvedValue(new Blob(['wav']));
    getUserMedia = vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] });
    Object.defineProperty(global.navigator, 'mediaDevices', {
      value: { getUserMedia },
      configurable: true,
    });
    global.MediaRecorder = MockMediaRecorder;
    global.fetch = vi.fn();
  });

  it('renders idle with a mic icon and no open feedback', () => {
    renderVoiceControl();
    expect(getFab()).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('requests the microphone and switches to the recording icon on click', async () => {
    renderVoiceControl();
    await userEvent.click(getFab());

    expect(getUserMedia).toHaveBeenCalledWith({ audio: true });
    expect(await screen.findByRole('button', { name: /click to stop recording/i })).toBeInTheDocument();
  });

  it('shows an error and stays idle when microphone access is denied', async () => {
    getUserMedia.mockRejectedValue(new Error('denied'));
    renderVoiceControl();
    await userEvent.click(getFab());

    expect(await screen.findByText('Microphone access was denied.')).toBeInTheDocument();
  });

  it('does nothing when clicked while transcribing (button disabled)', async () => {
    let resolveFetch;
    global.fetch.mockReturnValue(new Promise((resolve) => { resolveFetch = resolve; }));
    parseVoiceCommand.mockReturnValue(null);
    renderVoiceControl();

    await userEvent.click(getFab());
    await waitFor(() => expect(getFab()).not.toBeDisabled());
    await userEvent.click(getFab());

    await waitFor(() => expect(getFab()).toBeDisabled());
    // MUI sets `pointer-events: none` on a disabled Fab, which userEvent v14+
    // refuses to click by default; skip that check since this test is
    // deliberately exercising the disabled state.
    await userEvent.click(getFab(), { pointerEventsCheck: 0 });
    expect(global.fetch).toHaveBeenCalledTimes(1);

    // Let the pending transcription settle so it doesn't leak into the next test.
    resolveFetch({ ok: true, json: async () => ({ text: '' }) });
    await waitFor(() => expect(getFab()).not.toBeDisabled());
  });

  it('dispatches selectGenreOrCategory with the matched genre id on a "chooseGenre" command', async () => {
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ text: 'action movies' }) });
    parseVoiceCommand.mockReturnValue({ command: 'chooseGenre', genreOrCategory: 'action' });
    const { store } = renderVoiceControl();

    await recordAndStop();

    await waitFor(() => expect(store.getState().currentGenreOrCategory.genreIdOrCategoryName).toBe(28));
    expect(mockHistoryPush).toHaveBeenCalledWith('/');
    expect(await screen.findByText('Heard: "action movies"')).toBeInTheDocument();
  });

  it('falls back to the raw category string when no genre name matches', async () => {
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ text: 'popular' }) });
    parseVoiceCommand.mockReturnValue({ command: 'chooseGenre', genreOrCategory: 'popular' });
    const { store } = renderVoiceControl();

    await recordAndStop();

    await waitFor(() => expect(store.getState().currentGenreOrCategory.genreIdOrCategoryName).toBe('popular'));
  });

  it('calls setMode on a "changeMode" command', async () => {
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ text: 'dark mode' }) });
    parseVoiceCommand.mockReturnValue({ command: 'changeMode', mode: 'dark' });
    const setMode = vi.fn();
    renderVoiceControl(setMode);

    await recordAndStop();

    await waitFor(() => expect(setMode).toHaveBeenCalledWith('dark'));
  });

  it('clears auth tokens, dispatches clearUser, and redirects home on a "logout" command', async () => {
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ text: 'log out' }) });
    parseVoiceCommand.mockReturnValue({ command: 'logout' });
    const { store } = renderVoiceControl();

    await recordAndStop();

    await waitFor(() => expect(clearAuthTokens).toHaveBeenCalled());
    expect(store.getState().user.isAuthenticated).toBe(false);
    expect(mockHistoryPush).toHaveBeenCalledWith('/');
  });

  it('dispatches searchMovie with the parsed query on a "search" command', async () => {
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ text: 'search batman' }) });
    parseVoiceCommand.mockReturnValue({ command: 'search', query: 'batman' });
    const { store } = renderVoiceControl();

    await recordAndStop();

    await waitFor(() => expect(store.getState().currentGenreOrCategory.searchQuery).toBe('batman'));
    expect(mockHistoryPush).toHaveBeenCalledWith('/');
  });

  it('shows an info message when transcribed text matches no known command', async () => {
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ text: 'gibberish' }) });
    parseVoiceCommand.mockReturnValue(null);
    renderVoiceControl();

    await recordAndStop();

    expect(await screen.findByText('Heard: "gibberish" — no matching command.')).toBeInTheDocument();
  });

  it('shows a warning when nothing was transcribed', async () => {
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ text: '' }) });
    renderVoiceControl();

    await recordAndStop();

    expect(await screen.findByText("Didn't catch that — try again.")).toBeInTheDocument();
  });

  it('shows an error when the speech-to-text request fails', async () => {
    global.fetch.mockResolvedValue({ ok: false, status: 500 });
    renderVoiceControl();

    await recordAndStop();

    expect(await screen.findByText('Voice control is unavailable right now.')).toBeInTheDocument();
  });

  it('shows an error when the transcription request throws', async () => {
    global.fetch.mockRejectedValue(new Error('network down'));
    renderVoiceControl();

    await recordAndStop();

    expect(await screen.findByText('Voice control is unavailable right now.')).toBeInTheDocument();
  });

  it('dismisses the feedback Snackbar via its onClose', async () => {
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ text: '' }) });
    renderVoiceControl();

    await recordAndStop();
    await screen.findByText("Didn't catch that — try again.");

    act(() => {
      screen.getByRole('button', { name: /close/i }).click();
    });
    await waitFor(() => expect(screen.queryByText("Didn't catch that — try again.")).not.toBeInTheDocument());
  });
});
