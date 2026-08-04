// Tests the genreOrCategory redux slice: initial state and both reducers.
import reducer, { selectGenreOrCategory, searchMovie } from './currentGenreOrCategory';

describe('currentGenreOrCategory slice', () => {
  it('returns the initial state', () => {
    expect(reducer(undefined, { type: '@@INIT' })).toEqual({
      genreIdOrCategoryName: '',
      page: 1,
      searchQuery: '',
    });
  });

  it('selectGenreOrCategory sets the genre/category and clears any active search', () => {
    const state = { genreIdOrCategoryName: '', page: 1, searchQuery: 'batman' };
    const next = reducer(state, selectGenreOrCategory('popular'));
    expect(next.genreIdOrCategoryName).toBe('popular');
    expect(next.searchQuery).toBe('');
  });

  it('selectGenreOrCategory accepts a numeric genre id', () => {
    const next = reducer(undefined, selectGenreOrCategory(28));
    expect(next.genreIdOrCategoryName).toBe(28);
  });

  it('searchMovie sets the search query without touching the selected genre', () => {
    const state = { genreIdOrCategoryName: 'popular', page: 1, searchQuery: '' };
    const next = reducer(state, searchMovie('batman'));
    expect(next.searchQuery).toBe('batman');
    expect(next.genreIdOrCategoryName).toBe('popular');
  });
});
