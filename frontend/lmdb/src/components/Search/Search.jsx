import React, {
  useState, useRef, useEffect, useLayoutEffect, useCallback,
} from 'react';
import {
  TextField, InputAdornment, IconButton, CircularProgress,
  Tooltip, ToggleButtonGroup, ToggleButton, Snackbar, Alert,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Search as SearchIcon, Mic, Stop } from '@mui/icons-material';
import { useDispatch, useSelector } from 'react-redux';
import { useLocation } from 'react-router-dom';

import {
  aiSearchStarted,
  aiSearchSucceeded,
  aiSearchFailed,
  aiSearchCleared,
  querySpansReceived,
  querySpansCleared,
  dictatedQueryConsumed,
} from '../../features/currentGenreOrCategory';
import { useExecuteSearchMutation, useParseQueryMutation } from '../../services/AI';
import QueryHighlightOverlay from './QueryHighlightOverlay';
import HighlightLegend from './HighlightLegend';
import useStyles from './styles';
import { useVoiceControl } from '../VoiceControl/useVoiceControl';

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
  const theme = useTheme();
  const dispatch = useDispatch();
  const location = useLocation();
  const [executeSearch] = useExecuteSearchMutation();
  const [parseQuery] = useParseQueryMutation();
  // The most recent span breakdown #208 stored — rendered by QueryHighlightOverlay below (#209).
  const spans = useSelector((state) => state.currentGenreOrCategory.queryHighlightSpans);
  // A query VoiceControl.jsx's "search" command handed off via Redux (#199 AC5) — non-null exactly
  // once, until the effect below consumes it.
  const dictatedQuery = useSelector((state) => state.currentGenreOrCategory.dictatedQuery);
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
  // Positioned ancestor for the #209 overlay (styles.js's fieldWrapper), the real <input> DOM node,
  // and the overlay's own root — three refs the geometry-sync effect below ties together.
  const fieldWrapperRef = useRef(null);
  const inputElRef = useRef(null);
  const overlayRef = useRef(null);
  // Null until the first sync below measures the real input — gates rendering the overlay so it
  // never paints at a stale {0,0,0,0} position for one frame before that measurement lands.
  const [overlayRect, setOverlayRect] = useState(null);
  const hasQuery = query.length > 0;

  const { status, feedback, language, setDictationLanguage, toggleRecording, clearFeedback } = useVoiceControl();

  const handleLanguageChange = (event, newLanguage) => {
    if (!newLanguage) return;
    setDictationLanguage(newLanguage);
  };

  // Measures the real <input>'s box relative to fieldWrapperRef and positions the overlay exactly
  // over it — done by measurement rather than copying CSS padding/width numbers because the
  // startAdornment (search icon) and endAdornment (HighlightLegend, shown only while hasQuery) both
  // change the input's actual on-screen width/offset, and this way the overlay tracks that
  // automatically regardless of icon widths or breakpoint changes (#209: "stays correctly
  // positioned... without breaking its current layout").
  const syncOverlayRect = useCallback(() => {
    if (!inputElRef.current || !fieldWrapperRef.current) return;
    const inputBox = inputElRef.current.getBoundingClientRect();
    const wrapperBox = fieldWrapperRef.current.getBoundingClientRect();
    setOverlayRect({
      left: inputBox.left - wrapperBox.left,
      top: inputBox.top - wrapperBox.top,
      width: inputBox.width,
      height: inputBox.height,
    });
    // Mirror the real input's current horizontal scroll so a value already scrolled past the
    // field's visible width (e.g. right after the endAdornment appears/disappears and reflows it)
    // doesn't leave the overlay one frame out of sync.
    if (overlayRef.current) overlayRef.current.scrollLeft = inputElRef.current.scrollLeft;
  }, []);

  // Runs before paint (unlike useEffect) so the very first render that shows the overlay already
  // has correct geometry — no visible flash at {0,0,0,0}. Re-runs whenever hasQuery/spans change
  // the endAdornment's presence or the rendered text, either of which can shift the input's box.
  useLayoutEffect(() => {
    syncOverlayRect();
  }, [hasQuery, spans, syncOverlayRect]);

  // The input's box can also change for reasons outside query/spans changing: a window resize
  // (covered by the listener below), but also a layout shift that never changes the window's own
  // size at all — a sidebar/drawer toggle, a webfont swap, a sibling flex item in the NavBar
  // changing width. A ResizeObserver on the wrapper catches those too; window 'resize' stays as a
  // fallback since ResizeObserver isn't available in every test/legacy environment (jsdom included
  // — guarded here rather than assumed).
  useEffect(() => {
    window.addEventListener('resize', syncOverlayRect);
    let observer;
    if (typeof ResizeObserver !== 'undefined' && fieldWrapperRef.current) {
      observer = new ResizeObserver(syncOverlayRect);
      observer.observe(fieldWrapperRef.current);
    }
    return () => {
      window.removeEventListener('resize', syncOverlayRect);
      if (observer) observer.disconnect();
    };
  }, [syncOverlayRect]);

  // #209 AC2: as the user types past the field's visible width, the native <input> scrolls its own
  // content internally and fires 'scroll' — mirror that onto the overlay so its highlighted text
  // stays aligned with the (invisible) real text underneath instead of drifting out of sync.
  const handleInputScroll = () => {
    if (overlayRef.current && inputElRef.current) {
      overlayRef.current.scrollLeft = inputElRef.current.scrollLeft;
      overlayRef.current.scrollTop = inputElRef.current.scrollTop;
    }
  };

  // Only a pending debounce timer needs cleanup on unmount — parseQuery's own in-flight promise is
  // already guarded by latestParseRef above, so an unmount mid-request just lets it resolve unused.
  useEffect(() => () => {
    if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);
  }, []);

  // Runs the #207 parse-as-you-type call for `trimmed` and stores its span breakdown (#208) —
  // factored out of handleQueryChange's debounce callback so the #199 AC5 dictation effect below
  // can reuse the exact same request/staleness-guard/dispatch logic instead of a second copy of it.
  const runParse = useCallback((trimmed) => {
    latestParseRef.current = trimmed;
    parseQuery(trimmed)
      .unwrap()
      .then((response) => {
        // #208 AC2: a newer keystroke (or a dictated query landing mid-flight) may have superseded
        // this call while it was in flight.
        if (latestParseRef.current !== trimmed) return;
        dispatch(querySpansReceived({ spans: response.spans ?? [] }));
      })
      .catch(() => {
        // #208 AC3: a failed parse call must not clear or overwrite the last valid highlight
        // state — do nothing, same as staying with whatever was last successfully rendered.
      });
  }, [parseQuery, dispatch]);

  // Runs the #204 AC1 search-execute call for `trimmed` — factored out of handleKeyPress so the
  // #199 AC5 dictation effect below resolves a dictated query through the exact same pipeline
  // (and staleness guard) a typed-and-Entered query does, rather than duplicating this logic in
  // VoiceControl.jsx, which used to run it there and silently skip highlighting in the process.
  const runSearch = useCallback(async (trimmed) => {
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
  }, [executeSearch, dispatch]);

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
    debounceTimerRef.current = setTimeout(() => runParse(trimmed), QUERY_HIGHLIGHT_DEBOUNCE_MS);
  };

  const submitSearch = async () => {
    const trimmed = query.trim();
    if (!trimmed) {
      latestQueryRef.current = '';
      dispatch(aiSearchCleared());
      return;
    }
    await runSearch(trimmed);
  };

  const handleKeyDown = async (event) => {
    // Submit on Shift + Enter, allow newlines on Enter
    if (event.key === 'Enter' && event.shiftKey) {
      event.preventDefault(); // prevent new line from being added
      await submitSearch();
    }
  };

  // #199 AC5 / #210: a dictated query (VoiceControl.jsx's "search" command) lands here the same
  // way a typed-and-Entered one does.
  useEffect(() => {
    if (dictatedQuery === null) return;

    // 1. Show it in the field, exactly like a typed value would appear.
    setQuery(dictatedQuery);
    // 2. Cancel any typed-query debounce still pending from before this dictated query landed —
    //    without this, that stale timer could fire runParse() for the OLD typed value a moment
    //    after the dictated one, clobbering the highlighting this effect just triggered.
    if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);

    const trimmed = dictatedQuery.trim();
    if (trimmed) {
      // 3a. Non-blank: run the same two calls handleQueryChange's debounce and handleKeyPress's
      //     Enter would each eventually have run — immediately rather than debounced, since a
      //     dictated utterance arrives whole (no keystroke-by-keystroke pause to wait out).
      runParse(trimmed);
      runSearch(trimmed);
    } else {
      // 3b. Blank (in practice: only if ai-service's voice-command endpoint somehow classified
      //     silence as SEARCH) — mirror handleKeyPress's own empty-Enter branch fully, not just
      //     its highlight-clearing half, so no stale AI-search result set is left on screen.
      latestParseRef.current = '';
      latestQueryRef.current = '';
      dispatch(querySpansCleared());
      dispatch(aiSearchCleared());
    }
    // 4. Reset the Redux marker so this effect can't re-fire on a later, unrelated render.
    dispatch(dictatedQueryConsumed());
  }, [dictatedQuery, runParse, runSearch, dispatch]);

  if (location.pathname !== '/') return null;

  return (
    <div className={classes.searchContainer}>
      <div className={classes.fieldWrapper} ref={fieldWrapperRef} data-testid="search-field-wrapper">
        <TextField
          fullWidth
          onKeyDown={handleKeyDown}
          value={query}
          onChange={handleQueryChange}
          variant="standard"
          multiline
          minRows={1}
          maxRows={4}
          placeholder="Search for movies, or dictate a command..."
          inputRef={inputElRef}
          slotProps={{
            input: {
              disableUnderline: true,
              className: classes.input,
              startAdornment: (
                <InputAdornment position="start" sx={{ alignSelf: 'flex-start', mt: 1 }}>
                  <IconButton onClick={submitSearch} edge="start" sx={{ padding: 0 }}>
                    <SearchIcon />
                  </IconButton>
                </InputAdornment>
              ),
              endAdornment: (
                <InputAdornment position="end" sx={{ alignSelf: 'flex-end', mb: 0.5, display: 'flex', alignItems: 'center', gap: 1 }}>
                  {hasQuery && <HighlightLegend />}
                  <ToggleButtonGroup
                    value={language}
                    exclusive
                    size="small"
                    onChange={handleLanguageChange}
                    aria-label="Dictation language"
                    sx={{ height: 24 }}
                  >
                    <ToggleButton value="en" aria-label="English" sx={{ px: 1, fontSize: '0.7rem' }}>EN</ToggleButton>
                    <ToggleButton value="de" aria-label="German" sx={{ px: 1, fontSize: '0.7rem' }}>DE</ToggleButton>
                  </ToggleButtonGroup>
                  <Tooltip title={status === 'recording' ? 'Stop recording' : 'Dictate search or command'}>
                    <IconButton
                      color={status === 'recording' ? 'secondary' : 'primary'}
                      onClick={toggleRecording}
                      disabled={status === 'transcribing'}
                      size="small"
                    >
                      {status === 'transcribing' ? <CircularProgress size={20} color="inherit" /> : null}
                      {status === 'recording' && <Stop fontSize="small" />}
                      {status === 'idle' && <Mic fontSize="small" />}
                    </IconButton>
                  </Tooltip>
                </InputAdornment>
              ),
            },
            // Targets the native <textarea> itself
            htmlInput: {
              onScroll: handleInputScroll,
              style: hasQuery ? {
                color: 'transparent',
                caretColor: theme.palette.mode === 'light' ? 'black' : theme.palette.common.white,
              } : undefined,
            },
          }}
        />
        {hasQuery && overlayRect && (
          <QueryHighlightOverlay
            ref={overlayRef}
            query={query}
            spans={spans}
            style={{
              left: overlayRect.left,
              top: overlayRect.top,
              width: overlayRect.width,
              height: overlayRect.height,
            }}
          />
        )}
      </div>
      <Snackbar
        open={!!feedback}
        autoHideDuration={4000}
        onClose={clearFeedback}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {feedback && <Alert severity={feedback.severity} onClose={clearFeedback}>{feedback.message}</Alert>}
      </Snackbar>
    </div>
  );
}

export default Search;
