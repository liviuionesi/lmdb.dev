// Tests parseVoiceCommand's text->command matching (logout, theme, search, category, genre, no-match).
import { parseVoiceCommand } from './voiceCommands';

describe('parseVoiceCommand', () => {
  it('returns null for empty/blank/undefined input', () => {
    expect(parseVoiceCommand('')).toBeNull();
    expect(parseVoiceCommand('   ')).toBeNull();
    expect(parseVoiceCommand(undefined)).toBeNull();
  });

  it.each(['log out', 'logout', 'sign out', 'signout'])('recognizes "%s" as the logout command', (phrase) => {
    expect(parseVoiceCommand(phrase)).toEqual({ command: 'logout' });
  });

  it('recognizes "dark mode" as a changeMode command', () => {
    expect(parseVoiceCommand('switch to dark mode please')).toEqual({ command: 'changeMode', mode: 'dark' });
  });

  it('recognizes "light mode" as a changeMode command', () => {
    expect(parseVoiceCommand('light mode')).toEqual({ command: 'changeMode', mode: 'light' });
  });

  it('extracts the query from a "search for X" phrase', () => {
    expect(parseVoiceCommand('search for Batman')).toEqual({ command: 'search', query: 'batman' });
  });

  it('extracts the query from a "search X" phrase without "for"', () => {
    expect(parseVoiceCommand('Search Inception')).toEqual({ command: 'search', query: 'inception' });
  });

  it.each([
    ['show me top rated movies', 'top_rated'],
    ['popular', 'popular'],
    ['upcoming releases', 'upcoming'],
  ])('maps "%s" to the %s fixed category', (phrase, category) => {
    expect(parseVoiceCommand(phrase)).toEqual({ command: 'chooseGenre', genreOrCategory: category });
  });

  it('matches a real genre name supplied via genreNames', () => {
    expect(parseVoiceCommand('show me action movies', ['Action', 'Comedy'])).toEqual({
      command: 'chooseGenre',
      genreOrCategory: 'Action',
    });
  });

  it('returns null when nothing matches, even with genre names supplied', () => {
    expect(parseVoiceCommand('what is the weather today', ['Action', 'Comedy'])).toBeNull();
  });

  it('defaults genreNames to an empty list when omitted', () => {
    expect(parseVoiceCommand('random gibberish text')).toBeNull();
  });
});
