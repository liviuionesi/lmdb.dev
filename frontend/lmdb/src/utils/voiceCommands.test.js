// Tests parseVoiceCommand (#214): now a thin, language-agnostic client for ai-service's
// POST /api/v1/ai/voice-command - actual bilingual/phrasing-variance understanding moved
// server-side (covered by ai-service's AiServiceIntegrationTest), so these tests cover the
// request/response contract instead: what's sent, how each command shape maps back, and how a
// no-match/failure response is handled. resolveApiUrl is mocked the same way
// useBackendWakeup.test.js mocks it, rather than exercising the real health-check probe.
import { parseVoiceCommand } from './voiceCommands';
import { resolveApiUrl } from './apiUrl';

vi.mock('./apiUrl', () => ({ resolveApiUrl: vi.fn() }));

const jsonResponse = (body) => ({ ok: true, json: async () => body });

describe('parseVoiceCommand', () => {
  beforeEach(() => {
    resolveApiUrl.mockResolvedValue('https://api.lmdb.dev');
    global.fetch = vi.fn();
  });

  it('returns null for empty/blank/undefined input without calling the backend', async () => {
    await expect(parseVoiceCommand('')).resolves.toBeNull();
    await expect(parseVoiceCommand('   ')).resolves.toBeNull();
    await expect(parseVoiceCommand(undefined)).resolves.toBeNull();
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('posts the trimmed transcript and genreNames to /api/v1/ai/voice-command', async () => {
    global.fetch.mockResolvedValue(jsonResponse({ command: null, mode: null, genreOrCategory: null, query: null }));

    await parseVoiceCommand('  search for Batman  ', ['Action', 'Comedy']);

    expect(global.fetch).toHaveBeenCalledWith('https://api.lmdb.dev/api/v1/ai/voice-command', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ transcript: 'search for Batman', genreNames: ['Action', 'Comedy'] }),
    });
  });

  it('defaults genreNames to an empty array when omitted', async () => {
    global.fetch.mockResolvedValue(jsonResponse({ command: null, mode: null, genreOrCategory: null, query: null }));

    await parseVoiceCommand('log out');

    const [, requestInit] = global.fetch.mock.calls[0];
    expect(JSON.parse(requestInit.body).genreNames).toEqual([]);
  });

  it('maps a LOGOUT response to the logout command', async () => {
    global.fetch.mockResolvedValue(jsonResponse({ command: 'LOGOUT', mode: null, genreOrCategory: null, query: null }));

    await expect(parseVoiceCommand('log out')).resolves.toEqual({ command: 'logout' });
  });

  it.each([
    ['DARK', 'dark'],
    ['LIGHT', 'light'],
  ])('maps a CHANGE_MODE/%s response to a changeMode command with mode %s', async (wireMode, mappedMode) => {
    global.fetch.mockResolvedValue(
      jsonResponse({ command: 'CHANGE_MODE', mode: wireMode, genreOrCategory: null, query: null }),
    );

    await expect(parseVoiceCommand('switch theme')).resolves.toEqual({ command: 'changeMode', mode: mappedMode });
  });

  it('maps a CHOOSE_GENRE response carrying a real genre name to a chooseGenre command', async () => {
    global.fetch.mockResolvedValue(
      jsonResponse({ command: 'CHOOSE_GENRE', mode: null, genreOrCategory: 'Action', query: null }),
    );

    await expect(parseVoiceCommand('show me action movies', ['Action', 'Comedy'])).resolves.toEqual({
      command: 'chooseGenre',
      genreOrCategory: 'Action',
    });
  });

  it('maps a CHOOSE_GENRE response carrying a fixed category to a chooseGenre command', async () => {
    global.fetch.mockResolvedValue(
      jsonResponse({ command: 'CHOOSE_GENRE', mode: null, genreOrCategory: 'top_rated', query: null }),
    );

    await expect(parseVoiceCommand('show me top rated movies')).resolves.toEqual({
      command: 'chooseGenre',
      genreOrCategory: 'top_rated',
    });
  });

  it('maps a SEARCH response to a search command with its query', async () => {
    global.fetch.mockResolvedValue(
      jsonResponse({ command: 'SEARCH', mode: null, genreOrCategory: null, query: 'movies directed by nolan' }),
    );

    await expect(parseVoiceCommand('movies directed by nolan')).resolves.toEqual({
      command: 'search',
      query: 'movies directed by nolan',
    });
  });

  it('returns null when the backend found no confident match', async () => {
    global.fetch.mockResolvedValue(jsonResponse({ command: null, mode: null, genreOrCategory: null, query: null }));

    await expect(parseVoiceCommand('what is the weather today')).resolves.toBeNull();
  });

  // #214's whole point: bilingual and phrasing-variant transcripts resolve the same way as any
  // other, since this function no longer inspects the transcript's language or phrasing itself -
  // it just forwards whatever text it was given and maps back whatever command ai-service
  // classified. The German phrase below and its English equivalent stand in for that: both post
  // the same transcript shape and both map an identical response identically.
  it.each(['switch to dark mode please', 'dunkelmodus bitte'])(
    'forwards a transcript unchanged regardless of language ("%s")',
    async (transcript) => {
      global.fetch.mockResolvedValue(
        jsonResponse({ command: 'CHANGE_MODE', mode: 'DARK', genreOrCategory: null, query: null }),
      );

      const result = await parseVoiceCommand(transcript);

      const [, requestInit] = global.fetch.mock.calls[0];
      expect(JSON.parse(requestInit.body).transcript).toBe(transcript);
      expect(result).toEqual({ command: 'changeMode', mode: 'dark' });
    },
  );

  it('returns null for an unrecognized command string rather than throwing', async () => {
    global.fetch.mockResolvedValue(
      jsonResponse({ command: 'SOMETHING_NEW', mode: null, genreOrCategory: null, query: null }),
    );

    await expect(parseVoiceCommand('anything')).resolves.toBeNull();
  });

  it('throws when the backend responds with a non-ok status', async () => {
    global.fetch.mockResolvedValue({ ok: false, status: 500 });

    await expect(parseVoiceCommand('anything')).rejects.toThrow('voice-command parsing failed (500)');
  });

  it('propagates a network-level rejection', async () => {
    global.fetch.mockRejectedValue(new Error('network down'));

    await expect(parseVoiceCommand('anything')).rejects.toThrow('network down');
  });
});
