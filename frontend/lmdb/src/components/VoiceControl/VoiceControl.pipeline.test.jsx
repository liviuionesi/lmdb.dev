// Full-pipeline test for VoiceControl + parseVoiceCommand together (#216, Story #200 AC1):
// unlike VoiceControl.test.jsx (which mocks parseVoiceCommand to isolate the component's own
// idle -> recording -> transcribing state machine) and voiceCommands.test.js (which unit-tests
// parseVoiceCommand's request/response contract alone), this file exercises the real chain a
// user's dictation actually goes through: language switch -> POST /speech-to-text ->
// POST /voice-command -> command dispatch, for both supported languages. ai-service's own
// AiServiceIntegrationTest chains the same two HTTP endpoints server-side (speech-to-text output
// feeding voice-command classification); this is the frontend half, proving VoiceControl's
// dispatch wiring (runCommand) actually receives what the real parseVoiceCommand maps back from
// a backend response, not a hand-rolled shape a component-level mock could get wrong.
// MediaRecorder/getUserMedia/fetch aren't in jsdom, so each is stubbed at the top of the file,
// same as VoiceControl.test.jsx.
import React from 'react';
import { screen, waitFor } from '@testing-library/react';
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
import { ColorModeContext } from '../../utils/ToggleColorMode';

vi.mock('../../services/TMDB', () => ({ useGetGenresQuery: vi.fn() }));
vi.mock('../../services/AI', () => ({ useExecuteSearchMutation: vi.fn() }));
vi.mock('../../utils/wavEncoder', () => ({ encodeToWav: vi.fn() }));
// voiceCommands is deliberately NOT mocked here (see file header) - the real parseVoiceCommand
// runs and calls fetch itself, which is what makes this a full-pipeline test rather than a
// component-isolation one.

/**
 * Minimal MediaRecorder stand-in: `start()` flips state, `stop()` fires the component's own
 * `ondataavailable`/`onstop` handlers synchronously so tests can drive the record -> transcribe
 * flow without real audio I/O. Same shape as VoiceControl.test.jsx's own.
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
  renderWithProviders(
    <Provider store={store}>
      <ColorModeContext.Provider value={{ setMode }}>
        <VoiceControl />
      </ColorModeContext.Provider>
    </Provider>,
  );
  return { store };
};

// The Fab's accessible name changes with status; a feedback Snackbar adds its own "Close"
// button once shown, so tests must target the Fab specifically rather than assume it's the
// only button on the page.
const getFab = () => screen.getByRole('button', { name: /voice control|click to stop recording/i });

/** Clicks the Fab to start recording, then clicks it again to stop and let the real transcribe -> classify -> dispatch chain run. */
const recordAndStop = async () => {
  await userEvent.click(getFab());
  await waitFor(() => expect(getFab()).not.toBeDisabled());
  await userEvent.click(getFab());
};

/**
 * Builds a `global.fetch` stub that answers exactly the two real backend calls this pipeline
 * makes end to end - `/speech-to-text` (echoing `transcript`) and `/voice-command` (echoing
 * `voiceCommandResponse`, ai-service's wire shape) - plus a permissive default for anything else
 * (resolveApiUrl's own health-check probe), the same generic-success shape
 * VoiceControl.test.jsx already relies on for that probe.
 *
 * @param {string} transcript - text `/speech-to-text` should report as recognized
 * @param {object} voiceCommandResponse - the wire-shaped body `/voice-command` should return
 * @returns {Function} a mock implementation suitable for `global.fetch`
 */
const stubBackend = (transcript, voiceCommandResponse) => vi.fn(async (url) => {
  if (url.includes('/speech-to-text')) {
    return { ok: true, json: async () => ({ text: transcript }) };
  }
  if (url.includes('/voice-command')) {
    return { ok: true, json: async () => voiceCommandResponse };
  }
  return { ok: true, json: async () => ({}) };
});

describe('VoiceControl full pipeline (real parseVoiceCommand, mocked fetch)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    useGetGenresQuery.mockReturnValue({
      data: { genres: [{ id: 28, name: 'Action' }, { id: 35, name: 'Comedy' }] },
    });
    useExecuteSearchMutation.mockReturnValue([vi.fn().mockResolvedValue({ results: [] })]);
    encodeToWav.mockResolvedValue(new Blob(['wav']));
    Object.defineProperty(global.navigator, 'mediaDevices', {
      value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] }) },
      configurable: true,
    });
    global.MediaRecorder = MockMediaRecorder;
  });

  it('drives an English command end to end: EN language -> transcription -> intent parsing -> genre dispatch', async () => {
    // Given the default (English) dictation language, when a clip is recorded and stopped, then
    // the transcript /speech-to-text returns is what actually reaches /voice-command, and the
    // classified genre is what actually gets dispatched - not three independently-mocked stages
    // that merely happen to agree.
    global.fetch = stubBackend('show me action movies', {
      command: 'CHOOSE_GENRE', mode: null, genreOrCategory: 'Action', query: null,
    });
    const { store } = renderVoiceControl();

    await recordAndStop();

    await waitFor(() => expect(store.getState().currentGenreOrCategory.genreIdOrCategoryName).toBe(28));
    expect(await screen.findByText('Heard: "show me action movies"')).toBeInTheDocument();

    const [, speechToTextInit] = global.fetch.mock.calls.find(([url]) => url.includes('/speech-to-text'));
    expect(speechToTextInit.body.get('language')).toBe('en');
    const [, voiceCommandInit] = global.fetch.mock.calls.find(([url]) => url.includes('/voice-command'));
    const voiceCommandBody = JSON.parse(voiceCommandInit.body);
    expect(voiceCommandBody.transcript).toBe('show me action movies');
    expect(voiceCommandBody.genreNames).toEqual(['Action', 'Comedy']);
  });

  it('drives a German command end to end: DE language -> transcription -> intent parsing -> theme dispatch', async () => {
    // Same chain as the English case above, proving the pipeline composition isn't
    // English-specific: switching the dictation language sends `language=de` to
    // /speech-to-text, and the resulting German transcript reaches /voice-command and setMode
    // unmodified (#216 AC1's "for both languages").
    global.fetch = stubBackend('dunkelmodus bitte', {
      command: 'CHANGE_MODE', mode: 'DARK', genreOrCategory: null, query: null,
    });
    const setMode = vi.fn();
    renderVoiceControl(setMode);

    await userEvent.click(screen.getByRole('button', { name: 'German' }));
    await recordAndStop();

    await waitFor(() => expect(setMode).toHaveBeenCalledWith('dark'));
    expect(await screen.findByText('Heard: "dunkelmodus bitte"')).toBeInTheDocument();

    const [, speechToTextInit] = global.fetch.mock.calls.find(([url]) => url.includes('/speech-to-text'));
    expect(speechToTextInit.body.get('language')).toBe('de');
    const [, voiceCommandInit] = global.fetch.mock.calls.find(([url]) => url.includes('/voice-command'));
    const voiceCommandBody = JSON.parse(voiceCommandInit.body);
    expect(voiceCommandBody.transcript).toBe('dunkelmodus bitte');
    expect(voiceCommandBody.genreNames).toEqual(['Action', 'Comedy']);
  });
});
