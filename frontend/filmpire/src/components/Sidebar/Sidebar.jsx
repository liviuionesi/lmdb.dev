import React, { useEffect } from 'react';
import { Divider, List, ListItemButton, ListItemText, ListSubheader, ListItemIcon, Box, CircularProgress } from '@mui/material';
import { Link } from 'react-router-dom';
import { useTheme } from '@mui/material/styles';
import { useDispatch, useSelector } from 'react-redux';

import { selectGenreOrCategory } from '../../features/currentGenreOrCategory';
import { useGetGenresQuery } from '../../services/TMDB';
import useStyles from './styles';
import genreIcons from '../../assets/genres';

import LIMDbLogo from '../Logo/LIMDbLogo';

const categories = [
  { label: 'Popular', value: 'popular' },
  { label: 'Top Rated', value: 'top_rated' },
  { label: 'Upcoming', value: 'upcoming' },
];

function Sidebar({ setMobileOpen }) {
  const { genreIdOrCategoryName } = useSelector((state) => state.currentGenreOrCategory);
  const theme = useTheme();
  const { classes } = useStyles();
  const { data, isFetching } = useGetGenresQuery();
  const dispatch = useDispatch();

  useEffect(() => {
    setMobileOpen(false);
  }, [genreIdOrCategoryName]);

  return (
    <>
      <Link to="/" className={classes.imageLink} onClick={() => dispatch(selectGenreOrCategory('popular'))}>
        <LIMDbLogo width={190} height={46} className={classes.image} />
      </Link>
      <Divider />
      <List>
        <ListSubheader>Categories</ListSubheader>
        {categories.map(({ label, value }) => {
          const isSelected = value === genreIdOrCategoryName || (value === 'popular' && !genreIdOrCategoryName);
          return (
            <Link key={value} className={classes.links} to="/">
              <ListItemButton onClick={() => dispatch(selectGenreOrCategory(value))} selected={isSelected}>
                <ListItemIcon>
                  <img src={genreIcons[label.toLowerCase()]} className={classes.genreImage} height={30} alt={label} />
                </ListItemIcon>
                <ListItemText primary={label} />
              </ListItemButton>
            </Link>
          );
        })}
      </List>
      <Divider />
      <List>
        <ListSubheader>Genres</ListSubheader>
        {isFetching ? (
          <Box
            sx={{
              display: 'flex',
              justifyContent: 'center',
            }}
          >
            <CircularProgress />
          </Box>
        ) : data?.genres?.map(({ name, id }) => {
          const isSelected = String(genreIdOrCategoryName) === String(id);
          return (
            <Link key={name} className={classes.links} to="/">
              <ListItemButton onClick={() => dispatch(selectGenreOrCategory(id))} selected={isSelected}>
                <ListItemIcon>
                  <img src={genreIcons[name.toLowerCase()]} className={classes.genreImage} height={30} alt={name} />
                </ListItemIcon>
                <ListItemText primary={name} />
              </ListItemButton>
            </Link>
          );
        })}
      </List>
    </>
  );
}

export default Sidebar;
