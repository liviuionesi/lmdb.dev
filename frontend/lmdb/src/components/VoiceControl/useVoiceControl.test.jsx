import { renderHook, act, waitFor } from '@testing-library/react';
import { useContext } from 'react';
import { vi } from 'vitest';
import * as reactRedux from 'react-redux';
import * as reactRouterDom from 'react-router-dom';
import * as ToggleColorMode from '../../utils/ToggleColorMode';
import { useVoiceControl } from './useVoiceControl';
import * as tmdb from '../../services/TMDB';
import * as currentGenreOrCategory from '../../features/currentGenreOrCategory';
import * as auth from '../../features/auth';
import * as apiUrl from '../../utils/apiUrl';
import * as utils from '../../utils';
import * as wavEncoder from '../../utils/wavEncoder';
import * as voiceCommands from '../../utils/voiceCommands';
import * as dictationLanguage from '../../utils/dictationLanguage';

vi.mock('react', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    useContext: vi.fn(),
  };
});

vi.mock('react-redux', () => ({
  useDispatch: vi.fn(),
}));

vi.mock('react-router-dom', () => ({
  useNavigate: vi.fn(),
}));

vi.mock('../../services/TMDB', () => ({
  useGetGenresQuery: vi.fn(),
}));

vi.mock('../../utils/apiUrl', () => ({
  resolveApiUrl: vi.fn(),
}));

vi.mock('../../utils/wavEncoder', () => ({
  encodeToWav: vi.fn(),
}));

vi.mock('../../utils/voiceCommands', () => ({
  parseVoiceCommand: vi.fn(),
}));

vi.mock('../../utils', () => ({
  clearAuthTokens: vi.fn(),
}));

vi.mock('../../features/currentGenreOrCategory', () => ({
  selectGenreOrCategory: vi.fn(),
  dictatedQuerySubmitted: vi.fn(),
}));

vi.mock('../../features/auth', () => ({
  clearUser: vi.fn(),
}));

vi.mock('../../utils/dictationLanguage', () => ({
  getDictationLanguage: vi.fn(() => 'en-US'),
  setDictationLanguage: vi.fn(),
}));

describe('useVoiceControl', () => {
  let mockDispatch;
  let mockNavigate;
  let mockSetMode;
  let mockMediaRecorderInstance;

  beforeEach(() => {
    vi.clearAllMocks();

    mockDispatch = vi.fn();
    reactRedux.useDispatch.mockReturnValue(mockDispatch);

    mockNavigate = vi.fn();
    reactRouterDom.useNavigate.mockReturnValue(mockNavigate);

    mockSetMode = vi.fn();
    useContext.mockImplementation((context) => {
      if (context === ToggleColorMode.ColorModeContext) {
        return { setMode: mockSetMode };
      }
      return {};
    });

    tmdb.useGetGenresQuery.mockReturnValue({
      data: { genres: [{ id: 12, name: 'Adventure' }] },
    });

    apiUrl.resolveApiUrl.mockResolvedValue('http://localhost:8080');
    wavEncoder.encodeToWav.mockResolvedValue(new Blob());
    voiceCommands.parseVoiceCommand.mockResolvedValue(null);

    mockMediaRecorderInstance = {
      start: vi.fn(),
      stop: vi.fn().mockImplementation(function stop() {
        if (this.onstop) this.onstop();
      }),
      mimeType: 'audio/webm',
    };

    global.MediaRecorder = function MediaRecorderMock() {
      return mockMediaRecorderInstance;
    };
    Object.defineProperty(global.navigator, 'mediaDevices', {
      value: {
        getUserMedia: vi.fn().mockResolvedValue({
          getTracks: () => [{ stop: vi.fn() }],
        }),
      },
      configurable: true,
    });

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({ text: '' }),
    });
  });

  it('initializes with correct defaults', () => {
    const { result } = renderHook(() => useVoiceControl());
    expect(result.current.status).toBe('idle');
    expect(result.current.feedback).toBeNull();
    expect(result.current.language).toBe('en-US');
  });

  it('updates dictation language', () => {
    const { result } = renderHook(() => useVoiceControl());
    act(() => {
      result.current.setDictationLanguage('fr-FR');
    });
    expect(result.current.language).toBe('fr-FR');
    expect(dictationLanguage.setDictationLanguage).toHaveBeenCalledWith('fr-FR');
  });

  it('starts recording when idle and toggleRecording is called', async () => {
    const { result } = renderHook(() => useVoiceControl());
    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.status).toBe('recording'));
    expect(global.navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({ audio: true });
    expect(mockMediaRecorderInstance.start).toHaveBeenCalled();
  });

  it('handles microphone access denied', async () => {
    global.navigator.mediaDevices.getUserMedia.mockRejectedValueOnce(new Error('Denied'));
    const { result } = renderHook(() => useVoiceControl());
    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.feedback).toEqual({ severity: 'error', message: 'Microphone access was denied.' }));
    expect(result.current.status).toBe('idle');
  });

  it('stops recording and starts transcription when toggleRecording is called during recording', async () => {
    const { result } = renderHook(() => useVoiceControl());
    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.status).toBe('recording'));

    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.status).toBe('transcribing'));
    expect(mockMediaRecorderInstance.stop).toHaveBeenCalled();
  });

  it('handles empty transcription', async () => {
    const { result } = renderHook(() => useVoiceControl());
    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.status).toBe('recording'));

    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.feedback).toEqual({ severity: 'warning', message: "Didn't catch that — try again." }));
    expect(result.current.status).toBe('idle');
  });

  it('handles successful transcription with unmapped command', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({ text: 'blah blah' }),
    });
    voiceCommands.parseVoiceCommand.mockResolvedValue(null);

    const { result } = renderHook(() => useVoiceControl());
    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.status).toBe('recording'));

    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.feedback).toEqual({ severity: 'info', message: 'Heard: "blah blah" — no matching command.' }));
  });

  it('executes chooseGenre command with valid genre', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({ text: 'go to adventure' }),
    });
    voiceCommands.parseVoiceCommand.mockResolvedValue({
      command: 'chooseGenre',
      genreOrCategory: 'adventure',
    });
    currentGenreOrCategory.selectGenreOrCategory.mockReturnValue({ type: 'SELECT' });

    const { result } = renderHook(() => useVoiceControl());
    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.status).toBe('recording'));

    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/'));
    expect(mockDispatch).toHaveBeenCalledWith({ type: 'SELECT' });
    expect(currentGenreOrCategory.selectGenreOrCategory).toHaveBeenCalledWith(12);
  });

  it('executes chooseGenre command with fallback category', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({ text: 'go to popular' }),
    });
    voiceCommands.parseVoiceCommand.mockResolvedValue({
      command: 'chooseGenre',
      genreOrCategory: 'popular',
    });
    currentGenreOrCategory.selectGenreOrCategory.mockReturnValue({ type: 'SELECT_POP' });

    const { result } = renderHook(() => useVoiceControl());
    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.status).toBe('recording'));

    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(currentGenreOrCategory.selectGenreOrCategory).toHaveBeenCalledWith('popular'));
  });

  it('executes changeMode command', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({ text: 'dark mode' }),
    });
    voiceCommands.parseVoiceCommand.mockResolvedValue({
      command: 'changeMode',
      mode: 'dark',
    });

    const { result } = renderHook(() => useVoiceControl());
    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.status).toBe('recording'));

    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(mockSetMode).toHaveBeenCalledWith('dark'));
  });

  it('executes logout command', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({ text: 'log out' }),
    });
    voiceCommands.parseVoiceCommand.mockResolvedValue({
      command: 'logout',
    });
    auth.clearUser.mockReturnValue({ type: 'CLEAR_USER' });

    const { result } = renderHook(() => useVoiceControl());
    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.status).toBe('recording'));

    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(utils.clearAuthTokens).toHaveBeenCalled());
    expect(mockDispatch).toHaveBeenCalledWith({ type: 'CLEAR_USER' });
    expect(mockNavigate).toHaveBeenCalledWith('/');
  });

  it('executes search command', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({ text: 'search batman' }),
    });
    voiceCommands.parseVoiceCommand.mockResolvedValue({
      command: 'search',
      query: 'batman',
    });
    currentGenreOrCategory.dictatedQuerySubmitted.mockReturnValue({ type: 'SEARCH' });

    const { result } = renderHook(() => useVoiceControl());
    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.status).toBe('recording'));

    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/'));
    expect(mockDispatch).toHaveBeenCalledWith({ type: 'SEARCH' });
    expect(currentGenreOrCategory.dictatedQuerySubmitted).toHaveBeenCalledWith('batman');
  });

  it('handles fetch failure', async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      status: 500,
    });

    const { result } = renderHook(() => useVoiceControl());
    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.status).toBe('recording'));

    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.feedback).toEqual({ severity: 'error', message: 'Voice control is unavailable right now.' }));
  });

  it('clears feedback', () => {
    const { result } = renderHook(() => useVoiceControl());
    act(() => {
      result.current.clearFeedback();
    });
    expect(result.current.feedback).toBeNull();
  });

  it('accumulates chunks if data size > 0', async () => {
    const { result } = renderHook(() => useVoiceControl());
    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.status).toBe('recording'));

    act(() => {
      if (mockMediaRecorderInstance.ondataavailable) {
        mockMediaRecorderInstance.ondataavailable({ data: { size: 10, type: 'audio' } });
        mockMediaRecorderInstance.ondataavailable({ data: { size: 0, type: 'audio' } });
      }
    });

    act(() => {
      result.current.toggleRecording();
    });
    await waitFor(() => expect(result.current.status).toBe('idle'));
  });
});
