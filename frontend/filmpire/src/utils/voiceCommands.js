const CATEGORY_PHRASES = [
  { pattern: /\btop rated\b/, category: 'top_rated' },
  { pattern: /\bpopular\b/, category: 'popular' },
  { pattern: /\bupcoming\b/, category: 'upcoming' },
];

//* Turns Vosk's raw transcribed text into a {command, ...} payload (#68) — keeps App.jsx/NavBar's
//* downstream handling unchanged, only how the command is produced differs.
//* genreNames lets "show me action movies" match a real genre by name; the
//* three fixed categories above aren't in that list.
export const parseVoiceCommand = (rawText, genreNames = []) => {
  const text = (rawText || '').trim().toLowerCase();
  if (!text) {
    return null;
  }

  if (/\b(log ?out|sign ?out)\b/.test(text)) {
    return { command: 'logout' };
  }

  if (/\bdark mode\b/.test(text)) {
    return { command: 'changeMode', mode: 'dark' };
  }

  if (/\blight mode\b/.test(text)) {
    return { command: 'changeMode', mode: 'light' };
  }

  const searchMatch = text.match(/\bsearch(?: for)?\s+(.+)/);
  if (searchMatch) {
    return { command: 'search', query: searchMatch[1].trim() };
  }

  const matchedCategory = CATEGORY_PHRASES.find(({ pattern }) => pattern.test(text));
  if (matchedCategory) {
    return { command: 'chooseGenre', genreOrCategory: matchedCategory.category };
  }

  const matchedGenre = genreNames.find((name) => text.includes(name.toLowerCase()));
  if (matchedGenre) {
    return { command: 'chooseGenre', genreOrCategory: matchedGenre };
  }

  return null;
};
