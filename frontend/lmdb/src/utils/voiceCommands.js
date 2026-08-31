import { resolveApiUrl } from './apiUrl';

// Maps ai-service's uppercase VoiceCommandType wire values (#214) back to the lowerCamelCase
// command shape VoiceControl.jsx's runCommand already switches on (#68) - keeps that call site,
// and its existing tests, unchanged beyond parseVoiceCommand now being async.
const COMMAND_MAP = {
  LOGOUT: 'logout',
  CHANGE_MODE: 'changeMode',
  CHOOSE_GENRE: 'chooseGenre',
  SEARCH: 'search',
};

const MODE_MAP = { DARK: 'dark', LIGHT: 'light' };

//* Classifies a transcribed voice command into a {command, ...} payload via ai-service's
//* POST /api/v1/ai/voice-command (#214, Story #200) - LLM-based intent parsing that replaces the
//* per-language regex table this function used to run locally. Fixes #200's core gap: the old
//* table only ever matched fixed English phrases, so neither German transcripts nor phrasing
//* variance within English ("light mode" vs "switch to light mode") resolved reliably; the model
//* now handles both without a lookup table. genreNames is still what lets "show me action movies"
//* resolve to a real genre rather than one of the three fixed categories - unchanged from before,
//* just sent to the backend instead of matched here.
export const parseVoiceCommand = async (rawText, genreNames = []) => {
  const text = (rawText || '').trim();
  if (!text) {
    return null;
  }

  // Must await the health-checked resolver, same as the speech-to-text request this always
  // follows (see VoiceControl.jsx's transcribeAndRun) - a synchronous getApiUrl() could silently
  // hit a dead backend on a cold resolution cache.
  const baseUrl = await resolveApiUrl();
  const response = await fetch(`${baseUrl}/api/v1/ai/voice-command`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ transcript: text, genreNames }),
  });

  if (!response.ok) {
    throw new Error(`voice-command parsing failed (${response.status})`);
  }

  const { command, mode, genreOrCategory, query } = await response.json();
  const mappedCommand = COMMAND_MAP[command];
  // Backend command is null for "no confident match", same contract this function has always had.
  // An unrecognized command string (a future wire-format mismatch) degrades the same way, rather
  // than dispatching a command VoiceControl.jsx's runCommand doesn't know how to handle.
  if (!mappedCommand) {
    return null;
  }

  if (mappedCommand === 'changeMode') {
    return { command: mappedCommand, mode: MODE_MAP[mode] };
  }
  if (mappedCommand === 'chooseGenre') {
    return { command: mappedCommand, genreOrCategory };
  }
  if (mappedCommand === 'search') {
    return { command: mappedCommand, query };
  }
  return { command: mappedCommand };
};
