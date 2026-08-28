import React, { useState, useRef } from 'react';
import { TextField, InputAdornment } from '@mui/material';
import { Search as SearchIcon } from '@mui/icons-material';
import { useDispatch } from 'react-redux';
import { useLocation } from 'react-router-dom';

import { aiSearchStarted, aiSearchSucceeded, aiSearchFailed, aiSearchCleared } from '../../features/currentGenreOrCategory';
import { useExecuteSearchMutation } from '../../services/AI';
import useStyles from './styles';

// Maps one ai-service search result (movieId/title/overview/releaseDate/posterPath/voteAverage,
// see backend/ai-service/.../SearchResultMovieDto) into the TMDB-shaped movie object every
// rendering component in this app already expects (id/title/overview/poster_path/backdrop_path/
// vote_average) — ai-service owns its own response contract, this app's own (#204).
export const toTmdbMovieShape = (movie) => ({
  id: movie.movieId,
  title: movie.title,
  overview: movie.overview,
  poster_path: movie.posterPath,
  // ai-service's credit-derived results don't carry a distinct backdrop image — falling back to
  // the poster keeps FeaturedMovie showing something rather than a broken image.
  backdrop_path: movie.posterPath,
  release_date: movie.releaseDate,
  vote_average: movie.voteAverage,
});

function Search() {
  const [query, setQuery] = useState('');
  const { classes } = useStyles();
  const dispatch = useDispatch();
  const location = useLocation();
  const [executeSearch] = useExecuteSearchMutation();
  // Tracks which query is the MOST RECENTLY submitted one, so an out-of-order response from an
  // earlier, slower search can't overwrite a newer one's results — executeSearch is an imperative
  // mutation, not a query hook, so RTK Query's own per-arg de-dupe/cancellation doesn't apply here
  // the way it did for the old useGetMoviesQuery-driven flow.
  const latestQueryRef = useRef('');

  const handleKeyPress = async (event) => {
    if (event.key !== 'Enter') return;

    const trimmed = query.trim();
    if (!trimmed) {
      // #204 AC4: Enter on an empty box must keep clearing the current search (falls back to
      // popular movies via Movies.jsx's old query path), not call an endpoint that requires
      // non-blank input (ai-service's QueryParseRequestDto is @NotBlank) and surface it as an
      // error.
      latestQueryRef.current = '';
      dispatch(aiSearchCleared());
      return;
    }

    // #204 AC1: resolves through ai-service's natural-language query pipeline (#203) instead of
    // the old direct movie-service title search — see currentGenreOrCategory.js's Javadoc-style
    // comment for how this coexists with VoiceControl.jsx's own, not-yet-updated flow (#205).
    latestQueryRef.current = trimmed;
    dispatch(aiSearchStarted(trimmed));
    try {
      const response = await executeSearch(trimmed).unwrap();
      if (latestQueryRef.current !== trimmed) return; // superseded by a newer search
      dispatch(aiSearchSucceeded({ results: (response.results ?? []).map(toTmdbMovieShape) }));
    } catch {
      if (latestQueryRef.current !== trimmed) return;
      // #204 AC3: a failed ai-service call must not leave the UI blank — aiSearchFailed() flips
      // Movies.jsx into its own error-message rendering, the same path a movie-service outage
      // already used before this Task.
      dispatch(aiSearchFailed());
    }
  };

  if (location.pathname !== '/') return null;

  return (
    <div className={classes.searchContainer}>
      <TextField
        onKeyPress={handleKeyPress}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        variant="standard"
        slotProps={{
          input: {
            className: classes.input,
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon />
              </InputAdornment>
            ),
          },
        }}
      />
    </div>
  );
}

export default Search;
