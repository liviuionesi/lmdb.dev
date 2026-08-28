import React, { useState, useEffect } from 'react';
import { Box, CircularProgress, useMediaQuery, Typography } from '@mui/material';
import { useSelector } from 'react-redux';

import { useGetMoviesQuery } from '../../services/TMDB';
import { FeaturedMovie, MovieList, Pagination } from '..';

// Matches the network page size the old movie-service-backed query already used, so AI-search
// pagination (#204) "feels" the same even though it slices one already-fetched list instead of
// triggering a new network call per page — #203's aggregation endpoint returns one flat,
// unpaginated result set (capped at 200), not a paged one; see QueryAggregationService's own
// Javadoc for why.
const AI_SEARCH_PAGE_SIZE = 20;

function Movies() {
  const [page, setPage] = useState(1);
  const {
    genreIdOrCategoryName,
    searchQuery,
    aiSearchResults,
    aiSearchStatus,
  } = useSelector((state) => state.currentGenreOrCategory);
  // #204 AC1: a query submitted through Search.jsx resolves via ai-service (#203) instead of the
  // old direct movie-service title search below — `aiSearchStatus !== 'idle'` is exactly "a typed
  // search is active," set by currentGenreOrCategory.js's aiSearchStarted/Succeeded/Failed.
  const isAiSearch = aiSearchStatus !== 'idle';

  // A fresh AI search always starts back on page 1 — carrying over a stale page number from a
  // previous, possibly shorter result set would silently show an empty or wrong slice. Keyed off
  // the transition INTO 'loading' rather than the query string itself: resubmitting the exact same
  // query text still needs to reset the page, and the query string alone wouldn't change in that
  // case, so React's dependency comparison would skip the effect and leave the stale page number.
  useEffect(() => {
    if (aiSearchStatus === 'loading') setPage(1);
    // This project's ESLint config doesn't enable react-hooks/exhaustive-deps, so no disable
    // directive is needed for the deliberately narrow dependency array.
  }, [aiSearchStatus]);

  // `skip` (not a conditional hook call, which would break the Rules of Hooks) avoids firing the
  // old movie-service query at all while an AI search is active — #204 AC1's "instead of," not
  // "in addition to."
  const {
    data: queryData,
    error: queryError,
    isFetching: queryIsFetching,
  } = useGetMoviesQuery({ genreIdOrCategoryName, page, searchQuery }, { skip: isAiSearch });

  const lg = useMediaQuery((theme) => theme.breakpoints.only('lg'));
  const numberOfMovies = lg ? 17 : 19;

  let data;
  let error;
  let isFetching;
  if (isAiSearch) {
    isFetching = aiSearchStatus === 'loading';
    error = aiSearchStatus === 'failed';
    const allResults = aiSearchResults?.results ?? [];
    data = {
      results: allResults.slice((page - 1) * AI_SEARCH_PAGE_SIZE, page * AI_SEARCH_PAGE_SIZE),
      total_pages: Math.max(1, Math.ceil(allResults.length / AI_SEARCH_PAGE_SIZE)),
    };
  } else {
    data = queryData;
    error = queryError;
    isFetching = queryIsFetching;
  }

  if (isFetching) {
    return (
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
        }}
      >
        <CircularProgress size="4rem" />
      </Box>
    );
  }

  if (error) {
    return (
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          mt: '20px',
        }}
      >
        <Typography variant="h4">An error has occurred.</Typography>
      </Box>
    );
  }

  if (!data?.results?.length) {
    return (
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          mt: '20px',
        }}
      >
        <Typography variant="h4">
          No movies that match that name.
          <br />
          Please search for something else.
        </Typography>
      </Box>
    );
  }

  return (
    <div>
      <FeaturedMovie movie={data.results[0]} />
      <MovieList movies={data} numberOfMovies={numberOfMovies} excludeFirst />
      <Pagination currentPage={page} setPage={setPage} totalPages={data.total_pages} />
    </div>
  );
}

export default Movies;
