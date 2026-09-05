import React, { useState } from 'react';
import { Modal, Typography, Button, ButtonGroup, Grid, Box, CircularProgress, Rating } from '@mui/material';
import { Movie as MovieIcon, Theaters, Language, PlusOne, Favorite, FavoriteBorderOutlined, Remove, ArrowBack } from '@mui/icons-material';
import { Link, useParams } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';

import { selectGenreOrCategory } from '../../features/currentGenreOrCategory';
import useStyles from './styles';
import { useGetMovieQuery, useGetRecommendationsQuery } from '../../services/TMDB';
import {
  useGetFavoritesQuery,
  useGetWatchlistQuery,
  useAddFavoriteMutation,
  useRemoveFavoriteMutation,
  useAddToWatchlistMutation,
  useRemoveFromWatchlistMutation,
} from '../../services/user';
import genreIcons from '../../assets/genres';
import { MovieList } from '..';
import { userSelector } from '../../features/auth';
import ReviewMediaSection from './ReviewMediaSection';

function MovieInformation() {
  const { isAuthenticated } = useSelector(userSelector);
  const { id } = useParams();
  const { classes } = useStyles();
  const dispatch = useDispatch();
  const [open, setOpen] = useState(false);

  const { data, isFetching, error } = useGetMovieQuery(id);
  const { data: favorites } = useGetFavoritesQuery(undefined, { skip: !isAuthenticated });
  const { data: watchlist } = useGetWatchlistQuery(undefined, { skip: !isAuthenticated });
  const { data: recommendations } = useGetRecommendationsQuery({ list: '/recommendations', movie_id: id });

  const [addFavorite] = useAddFavoriteMutation();
  const [removeFavorite] = useRemoveFavoriteMutation();
  const [addToWatchlistMutation] = useAddToWatchlistMutation();
  const [removeFromWatchlist] = useRemoveFromWatchlistMutation();

  const isMovieFavorited = !!favorites?.some((entry) => entry.movieId === Number(id));
  const isMovieWatchlisted = !!watchlist?.some((entry) => entry.movieId === Number(id));

  const addToFavorites = async () => {
    if (isMovieFavorited) {
      await removeFavorite(id);
    } else {
      await addFavorite(id);
    }
  };

  const addToWatchlist = async () => {
    if (isMovieWatchlisted) {
      await removeFromWatchlist(id);
    } else {
      await addToWatchlistMutation(id);
    }
  };

  if (isFetching) {
    return (
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
        }}
      >
        <CircularProgress size="8rem" />
      </Box>
    );
  }

  if (error) {
    return (
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
        }}
      >
        <Link to="/">Something has gone wrong - Go back</Link>
      </Box>
    );
  }

  return (
    <Grid container className={classes.containerSpaceAround}>
      <Grid
        style={{ display: 'flex', marginBottom: '30px' }}
        size={{
          sm: 12,
          lg: 4,
        }}
      >
        <img
          className={classes.poster}
          src={`https://image.tmdb.org/t/p/w500/${data?.poster_path}`}
          alt={data?.title}
        />
      </Grid>
      <Grid
        container
        direction="column"
        size={{
          lg: 7,
        }}
      >
        <Typography variant="h3" align="center" gutterBottom>
          {data?.title} ({data.release_date.split('-')[0]})
        </Typography>
        <Typography variant="h5" align="center" gutterBottom>
          {data?.tagline}
        </Typography>
        <Grid className={classes.containerSpaceAround}>
          <Box
            align="center"
            sx={{
              display: 'flex',
            }}
          >
            <Rating readOnly value={data.vote_average / 2} />
            <Typography variant="subtitle1" gutterBottom style={{ marginLeft: '10px' }}>
              {data?.vote_average} / 10
            </Typography>
          </Box>
          <Typography variant="h6" align="center" gutterBottom>
            {data?.runtime}min | Language: {data?.spoken_languages?.[0]?.name}
          </Typography>
        </Grid>
        <Grid className={classes.genresContainer}>
          {data?.genres?.map((genre) => (
            <Link
              key={genre.name}
              className={classes.links}
              to="/"
              onClick={() => dispatch(selectGenreOrCategory(genre.id))}
            >
              <img src={genreIcons[genre.name.toLowerCase()]} alt={genre.name} className={classes.genreImage} height={30} />
              <Typography color="textPrimary" variant="subtitle1">
                {genre?.name}
              </Typography>
            </Link>
          )) }
        </Grid>
        <Typography variant="h5" gutterBottom style={{ marginTop: '10px' }}>
          Overview
        </Typography>
        <Typography style={{ marginBottom: '2rem' }}>
          {data?.overview}
        </Typography>
        <Typography variant="h5" gutterBottom>Top Cast</Typography>
        <Grid container spacing={2}>
          {data?.credits?.cast?.map((character, i) => (
            character.profile_path && (
              <Grid
                key={character.id || i}
                component={Link}
                to={`/actors/${character.id}`}
                style={{ textDecoration: 'none' }}
                size={{
                  xs: 4,
                  md: 2,
                }}
              >
                <img className={classes.castImage} src={`https://image.tmdb.org/t/p/w500/${character.profile_path}`} alt={character.name} />
                <Typography color="textPrimary">{character?.name}</Typography>
                <Typography color="textSecondary">
                  {character.character.split('/')[0]}
                </Typography>
              </Grid>
            )
          )).slice(0, 6)}
        </Grid>
        <Grid container style={{ marginTop: '2rem' }}>
          <div className={classes.buttonsContainer}>
            <Grid
              className={classes.buttonsContainer}
              size={{
                xs: 12,
                sm: 6,
              }}
            >
              <ButtonGroup size="small" variant="outlined">
                <Button target="_blank" rel="noopener noreferrer" href={data?.homepage} endIcon={<Language />}>Website</Button>
                <Button target="_blank" rel="noopener noreferrer" href={`https://www.imdb.com/title/${data?.imdb_id}`} endIcon={<MovieIcon />}>IMDB</Button>
                <Button onClick={() => setOpen(true)} href="#" endIcon={<Theaters />}>Trailer</Button>
              </ButtonGroup>
            </Grid>
            <Grid
              className={classes.buttonsContainer}
              size={{
                xs: 12,
                sm: 6,
              }}
            >
              <ButtonGroup size="medium" variant="outlined">
                <Button onClick={addToFavorites} endIcon={isMovieFavorited ? <FavoriteBorderOutlined /> : <Favorite />}>
                  {isMovieFavorited ? 'Unfavorite' : 'Favorite'}
                </Button>
                <Button onClick={addToWatchlist} endIcon={isMovieWatchlisted ? <Remove /> : <PlusOne />}>
                  Watchlist
                </Button>
                <Button endIcon={<ArrowBack />} sx={{ borderColor: 'primary.main' }}>
                  <Typography
                    style={{ textDecoration: 'none' }}
                    component={Link}
                    to="/"
                    variant="subtitle2"
                    sx={{
                      color: 'inherit',
                    }}
                  >
                    Back
                  </Typography>
                </Button>
              </ButtonGroup>
            </Grid>
          </div>
        </Grid>
      </Grid>
      <ReviewMediaSection movieId={id} />
      <Box
        sx={{
          marginTop: '5rem',
          width: '100%',
        }}
      >
        <Typography variant="h3" gutterBottom align="center">
          You might also like
        </Typography>
        {recommendations
          ? <MovieList movies={recommendations} numberOfMovies={12} />
          : <Box>Sorry, nothing was found.</Box>}
      </Box>
      <Modal
        closeAfterTransition
        className={classes.modal}
        open={open}
        onClose={() => setOpen(false)}
      >
        <Box className={classes.video}>
          {data?.videos?.results?.length > 0 ? (
            <iframe
              autoPlay
              width="100%"
              height="100%"
              style={{ border: 0 }}
              title="Trailer"
              src={`https://www.youtube.com/embed/${data.videos.results[0].key}`}
              allow="autoplay"
            />
          ) : (
            <Typography variant="h6" align="center">No trailer available.</Typography>
          )}
        </Box>
      </Modal>
    </Grid>
  );
}

export default MovieInformation;
