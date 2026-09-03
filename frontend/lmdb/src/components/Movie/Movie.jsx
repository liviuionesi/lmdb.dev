import React from 'react';
import { Typography, Grid, Grow, Tooltip, Rating } from '@mui/material';
import { Link } from 'react-router-dom';

import useStyles from './styles';

// `caption` is optional (#221): the recommendations view is this component's only caller that
// passes one, to show ai-service's explanation text under an otherwise ordinary movie card
// instead of duplicating this whole card just to add one line.
function Movie({ movie, i, caption }) {
  const { classes } = useStyles();

  return (
    <Grid
      className={classes.movie}
      size={{
        xs: 12,
        sm: 6,
        md: 4,
        lg: 3,
        xl: 2,
      }}
    >
      <Grow in key={i} timeout={(i + 1) * 250}>
        <Link className={classes.links} to={`/movie/${movie.id}`}>
          <img
            alt={movie.title}
            className={classes.image}
            src={movie.poster_path ? `https://image.tmdb.org/t/p/w500/${movie.poster_path}` : 'https://www.fillmurray.com/200/300'}
          />
          <Typography className={classes.title} variant="h5">{movie.title}</Typography>
          <Tooltip disableTouchListener title={`${movie.vote_average} / 10`}>
            <div>
              <Rating readOnly value={movie.vote_average / 2} precision={0.1} />
            </div>
          </Tooltip>
          {caption && <Typography className={classes.caption} variant="body2">{caption}</Typography>}
        </Link>
      </Grow>
    </Grid>
  );
}

export default Movie;
