import React from 'react';
import { Typography, Box } from '@mui/material';

import { Movie } from '..';
import { useGetMovieQuery } from '../../services/TMDB';
import useStyles from './styles';

// user-service's favorites/watchlist entries carry only a movieId, so each
// card hydrates its own movie details via the TMDB facade rather than the
// parent fetching them all at once (keeps the movieId->movie fetch inside
// the rules of hooks instead of calling a hook in a loop).
function FavoriteMovie({ movieId, i }) {
  const { data: movie, isFetching } = useGetMovieQuery(movieId);

  if (isFetching || !movie) {
    return null;
  }

  return <Movie movie={movie} i={i} />;
}

function RatedCards({ title, movieIds }) {
  const { classes } = useStyles();

  return (
    <Box>
      <Typography variant="h5" gutterBottom>{title}</Typography>
      <Box
        className={classes.container}
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
        }}
      >
        {movieIds?.map((movieId, i) => (
          <FavoriteMovie key={movieId} movieId={movieId} i={i} />
        ))}
      </Box>
    </Box>
  );
}

export default RatedCards;
