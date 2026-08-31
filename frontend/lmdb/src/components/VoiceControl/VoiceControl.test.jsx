// Tests VoiceControl (#68): the click-to-talk Fab that records via the
// browser mic APIs, sends the clip to ai-service for transcription, and
// dispatches the resulting voice command; and its EN/DE dictation-language
// switch (#213). encodeToWav/parseVoiceCommand are unit-tested in their own
// files, so they're mocked here to isolate VoiceControl's own state machine
// (idle -> recording -> transcribing) and its command-dispatch branches -
// parseVoiceCommand's own bilingual/phrasing-variance behavior moved
// server-side (#214), so this file only needs to cover that it's awaited
// and that a rejection is handled, not what any particular transcript maps to.
// dictationLanguage.js's own get/set behavior is unit-tested separately in
// dictationLanguage.test.js - here it's exercised through real
// localStorage (cleared in beforeEach) so persistence-across-mount is
// actually verified end to end. MediaRecorder/getUserMedia/fetch aren't in
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
import { useExecuteSearchMutation } from '../../services/AI';
import { encodeToWav } from '../../utils/wavEncoder';
import { parseVoiceCommand } from '../../utils/voiceCommands';
import { clearAuthTokens } from '../../utils';
import { ColorModeContext } from '../../utils/ToggleColorMode';

vi.mock('../../services/TMDB', () => ({ useGetGenresQuery: vi.fn() }));
vi.mock('../../services/AI', () => ({ useExecuteSearchMutation: vi.fn() }));
vi.mock('../../utils/wavEncoder', () => ({ encodeToWav: vi.fn() }));
vi.mock('../../utils/voiceCommands', () => ({ parseVoiceCommand: vi.fn() }));
// Vitest hoists vi.mock calls above imports, so the real module is fetched
// via the `importOriginal` callback rather than a synchronous requireActual.
vi.mock('../../utils', async (importOriginal) => ({
  ...(await importOriginal()),
  clearAuthTokens: vi.fn(),
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal()),
  useNavigate: () => mockNavigate,
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
    localStorage.clear();
    useGetGenresQuery.mockReturnValue({
      data: { genres: [{ id: 28, name: 'Action' }, { id: 35, name: 'Comedy' }] },
    });
    useExecuteSearchMutation.mockReturnValue([vi.fn().mockResolvedValue({ results: [] })]);
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

  it('defaults the dictation-language switch to English for a first-time user', () => {
    // Given no prior visit (localStorage cleared in beforeEach), when
    // VoiceControl mounts, then the switch reflects the same English
    // default getDictationLanguage() returns - not a separately hardcoded
    // 'en' in the component that could drift from the util's default.
    renderVoiceControl();
    expect(screen.getByRole('button', { name: 'English', pressed: true })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'German', pressed: false })).toBeInTheDocument();
  });

  it('restores a previously-persisted German selection on mount', () => {
    // Given a prior session already chose German, when VoiceControl mounts
    // fresh, then it reads that persisted choice via getDictationLanguage()
    // rather than always starting from English (#213's "persists across
    // page reloads" criterion).
    localStorage.setItem('lmdb_dictation_language', 'de');
    renderVoiceControl();
    expect(screen.getByRole('button', { name: 'German', pressed: true })).toBeInTheDocument();
  });

  it('falls back to English when localStorage holds an unsupported/corrupted value', () => {
    // Given storage was corrupted or written by an old build (e.g. a code
    // neither 'en' nor 'de'), when VoiceControl mounts, then it must not
    // render that raw value as "selected" nor forward it to the backend -
    // covers the component actually going through getDictationLanguage()'s
    // sanitizing rather than reading localStorage raw itself.
    localStorage.setItem('lmdb_dictation_language', 'fr');
    renderVoiceControl();
    expect(screen.getByRole('button', { name: 'English', pressed: true })).toBeInTheDocument();
  });

  it('persists a language switch and sends it with the next speech-to-text request', async () => {
    // Given the user switches to German, when they record and stop, then
    // both the persisted value and the outgoing request's `language` field
    // reflect German - asserts the actual FormData field, not just a
    // truthy check, so a hardcoded/wrong field name would fail this.
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ text: '' }) });
    renderVoiceControl();

    await userEvent.click(screen.getByRole('button', { name: 'German' }));
    expect(localStorage.getItem('lmdb_dictation_language')).toBe('de');

    await recordAndStop();

    // resolveApiUrl's own health-check probe shares this mocked fetch (and,
    // depending on test order, its resolution cache may already be warm from
    // an earlier test) - find the actual speech-to-text call by URL rather
    // than assuming it's fetch's first/only call.
    const [, requestInit] = global.fetch.mock.calls.find(([url]) => url.includes('/speech-to-text'));
    expect(requestInit.body.get('language')).toBe('de');
  });

  it('keeps sending the switched language on a second request without switching again', async () => {
    // Given the user switched to German once, when they record and stop
    // *twice* without touching the switch again, then both requests carry
    // German (#213: "sent with every ... request until changed") - guards
    // against a regression that resets `language` back to the default
    // between recordings (e.g. in the transcribeAndRun `finally` block).
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ text: '' }) });
    renderVoiceControl();

    await userEvent.click(screen.getByRole('button', { name: 'German' }));
    await recordAndStop();
    await recordAndStop();

    const speechToTextCalls = global.fetch.mock.calls.filter(([url]) => url.includes('/speech-to-text'));
    expect(speechToTextCalls).toHaveLength(2);
    speechToTextCalls.forEach(([, requestInit]) => {
      expect(requestInit.body.get('language')).toBe('de');
    });
  });

  it('does not clear the selection when the already-active language button is clicked again', async () => {
    // Given English is already selected, when its own toggle button is
    // clicked again, then it stays selected - MUI's exclusive
    // ToggleButtonGroup passes `null` on a re-click of the active option,
    // and the handler must ignore that rather than clearing the selection
    // to an unsupported empty state.
    renderVoiceControl();

    await userEvent.click(screen.getByRole('button', { name: 'English' }));

    expect(screen.getByRole('button', { name: 'English', pressed: true })).toBeInTheDocument();
  });

  it('keeps recording uninterrupted and uses the newly-selected language when switched mid-recording', async () => {
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ text: '' }) });
    renderVoiceControl();

    await userEvent.click(getFab());
    await waitFor(() => expect(getFab()).not.toBeDisabled());

    // Switching language while a recording is already in progress must not
    // interrupt it (#213's "doesn't ... break an in-progress recording").
    await userEvent.click(screen.getByRole('button', { name: 'German' }));
    expect(screen.getByRole('button', { name: /click to stop recording/i })).toBeInTheDocument();

    await userEvent.click(getFab());

    const [, requestInit] = global.fetch.mock.calls.find(([url]) => url.includes('/speech-to-text'));
    expect(requestInit.body.get('language')).toBe('de');
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
    expect(mockNavigate).toHaveBeenCalledWith('/');
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
    expect(mockNavigate).toHaveBeenCalledWith('/');
  });

  it('dispatches AI search flow with the parsed query on a "search" command', async () => {
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ text: 'movies directed by nolan' }) });
    parseVoiceCommand.mockReturnValue({ command: 'search', query: 'movies directed by nolan' });
    
    const mockExecuteSearch = vi.fn().mockReturnValue({
      unwrap: () => Promise.resolve({ results: [{ movieId: 123, title: 'Inception' }] })
    });
    useExecuteSearchMutation.mockReturnValue([mockExecuteSearch]);
    
    const { store } = renderVoiceControl();

    await recordAndStop();

    await waitFor(() => expect(store.getState().currentGenreOrCategory.aiSearchStatus).toBe('succeeded'));
    expect(store.getState().currentGenreOrCategory.aiSearchQuery).toBe('movies directed by nolan');
    expect(mockNavigate).toHaveBeenCalledWith('/');
    expect(store.getState().currentGenreOrCategory.aiSearchResults).toEqual({
      results: [{
        id: 123,
        title: 'Inception',
        overview: undefined,
        poster_path: undefined,
        backdrop_path: undefined,
        release_date: undefined,
        vote_average: undefined
      }]
    });
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

  it('shows an error when the (now-async, #214) command-classification call rejects', async () => {
    // parseVoiceCommand became a network call to ai-service's voice-command endpoint (#214) - it
    // can now reject the way the speech-to-text fetch already could, and transcribeAndRun must
    // await it inside the same try/catch rather than letting an unhandled rejection escape.
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ text: 'action movies' }) });
    parseVoiceCommand.mockRejectedValue(new Error('voice-command parsing failed (500)'));
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
