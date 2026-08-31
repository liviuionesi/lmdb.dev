import React, { useState, useRef, useEffect } from 'react';
import { TextField, InputAdornment } from '@mui/material';
import { Search as SearchIcon } from '@mui/icons-material';
import { useDispatch } from 'react-redux';
import { useLocation } from 'react-router-dom';

import {
  aiSearchStarted,
  aiSearchSucceeded,
  aiSearchFailed,
  aiSearchCleared,
  querySpansReceived,
  querySpansCleared,
} from '../../features/currentGenreOrCategory';
import { useExecuteSearchMutation, useParseQueryMutation } from '../../services/AI';
import useStyles from './styles';

// How long a typing pause must last before a debounced parse-as-you-type call fires (#208 AC1) —
// short enough to feel live, long enough that a normal typing cadence collapses to one call per
// pause rather than one per keystroke.
export const QUERY_HIGHLIGHT_DEBOUNCE_MS = 400;

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
  const [parseQuery] = useParseQueryMutation();
  // Tracks which query is the MOST RECENTLY submitted one, so an out-of-order response from an
  // earlier, slower search can't overwrite a newer one's results — executeSearch is an imperative
  // mutation, not a query hook, so RTK Query's own per-arg de-dupe/cancellation doesn't apply here
  // the way it did for the old useGetMoviesQuery-driven flow.
  const latestQueryRef = useRef('');
  // Same stale-response guard, but for the separate debounced parse-as-you-type flow (#208 AC2) —
  // kept apart from latestQueryRef above since the two calls (execute on Enter, parse on every
  // debounced keystroke) run independently and can be in flight at the same time.
  const latestParseRef = useRef('');
  const debounceTimerRef = useRef(null);

  // Only a pending debounce timer needs cleanup on unmount — parseQuery's own in-flight promise is
  // already guarded by latestParseRef above, so an unmount mid-request just lets it resolve unused.
  useEffect(() => () => {
    if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);
  }, []);

  const handleQueryChange = (event) => {
    const { value } = event.target;
    setQuery(value);

    // 1. Cancel any already-scheduled parse call — only the pause after the LAST keystroke should
    //    actually fire one (#208 AC1), not one per keystroke.
    if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);

    const trimmed = value.trim();
    if (!trimmed) {
      // Emptying the box exits live-highlight mode immediately; no need to wait out the debounce
      // for a call ai-service would reject as blank input anyway.
      latestParseRef.current = '';
      dispatch(querySpansCleared());
      return;
    }

    // 2. Schedule the parse call for after the debounce pause.
    debounceTimerRef.current = setTimeout(() => {
      latestParseRef.current = trimmed;
      parseQuery(trimmed)
        .unwrap()
        .then((response) => {
          // #208 AC2: a newer keystroke may have superseded this call while it was in flight.
          if (latestParseRef.current !== trimmed) return;
          dispatch(querySpansReceived({ spans: response.spans ?? [] }));
        })
        .catch(() => {
          // #208 AC3: a failed parse call must not clear or overwrite the last valid highlight
          // state — do nothing, same as staying with whatever was last successfully rendered.
        });
    }, QUERY_HIGHLIGHT_DEBOUNCE_MS);
  };

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
        onChange={handleQueryChange}
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
