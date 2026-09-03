import React from 'react';
import { Box, CircularProgress, Grid, Typography } from '@mui/material';
import { useSelector } from 'react-redux';
import { Navigate } from 'react-router-dom';

import { useGetMovieRecommendationsQuery } from '../../services/AI';
import { useGetMovieQuery } from '../../services/TMDB';
import { userSelector } from '../../features/auth';
import { Movie } from '..';
import useStyles from './styles';

/**
 * One recommended card: ai-service only ever returns a movieId/score/reason (#220), never the
 * movie's own details, so each card hydrates its poster/title/rating itself via the TMDB facade —
 * the same per-id pattern RatedCards.jsx's FavoriteMovie uses for favorites/watchlist cards. The
 * `reason` text renders as Movie's optional caption (#221 AC2 / #196 AC4), not a bare movie card.
 */
function RecommendedMovie({ recommendation, i }) {
  const { data: movie, isFetching } = useGetMovieQuery(Number(recommendation.movieId));

  if (isFetching || !movie) {
    return null;
  }

  return <Movie movie={movie} i={i} caption={recommendation.reason} />;
}

/**
 * The Recommendations view (#221): AI-generated picks based on the user's own Favorites (#219),
 * fetched through #220's `getMovieRecommendations`. Requires a signed-in user (favorites are
 * per-account), so it redirects home otherwise, the same way Profile.jsx does.
 */
function Recommendations() {
  const { isAuthenticated } = useSelector(userSelector);
  const { classes } = useStyles();
  const { data, error, isFetching } = useGetMovieRecommendationsQuery(undefined, { skip: !isAuthenticated });

  if (!isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  // Loading: the same centered spinner Movies.jsx uses for its own network states.
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

  // Error: distinct from the empty-history message below — this means the call itself failed
  // (including the case where every favorited movie's TMDB lookup failed, #220's Notes), not that
  // the user simply has no favorites yet.
  if (error) {
    return (
      <Box className={classes.message}>
        <Typography variant="h4">Couldn&apos;t load recommendations. Please try again later.</Typography>
      </Box>
    );
  }

  // Empty history (#219/#220's isEmpty): a real, distinct state from both loading and error.
  if (data?.isEmpty) {
    return (
      <Box className={classes.message}>
        <Typography variant="h4">
          Favorite a few movies to get personalized recommendations.
        </Typography>
      </Box>
    );
  }

  // A non-empty history that still comes back with zero picks (e.g. movie-service has no
  // candidates to offer, per RecommendationService's own Javadoc) — distinct from "no history yet"
  // above, but just as much a case that shouldn't render a bare, empty "Recommended For You" grid.
  if (!data?.recommendations?.length) {
    return (
      <Box className={classes.message}>
        <Typography variant="h4">No recommendations available right now. Please try again later.</Typography>
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>Recommended For You</Typography>
      <Grid container className={classes.container}>
        {data.recommendations.map((recommendation, i) => (
          <RecommendedMovie key={recommendation.movieId} recommendation={recommendation} i={i} />
        ))}
      </Grid>
    </Box>
  );
}

export default Recommendations;
