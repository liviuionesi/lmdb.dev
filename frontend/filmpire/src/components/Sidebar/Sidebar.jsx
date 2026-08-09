import React, { useEffect } from 'react';
import { Divider, List, ListItemButton, ListItemText, ListSubheader, ListItemIcon, Box, CircularProgress } from '@mui/material';
import { Link } from 'react-router-dom';
import { useTheme } from '@mui/material/styles';
import { useDispatch, useSelector } from 'react-redux';

import { selectGenreOrCategory } from '../../features/currentGenreOrCategory';
import { useGetGenresQuery } from '../../services/TMDB';
import useStyles from './styles';
import genreIcons from '../../assets/genres';

const categories = [
  { label: 'Popular', value: 'popular' },
  { label: 'Top Rated', value: 'top_rated' },
  { label: 'Upcoming', value: 'upcoming' },
];

const redLogo = 'https://fontmeme.com/permalink/210930/8531c658a743debe1e1aa1a2fc82006e.png';
const blueLogo = 'https://fontmeme.com/permalink/210930/6854ae5c7f76597cf8680e48a2c8a50a.png';

const Sidebar = ({ setMobileOpen }) => {
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
        <img
          className={classes.image}
          src={theme.palette.mode === 'light' ? redLogo : blueLogo}
          alt="Filmpire logo"
        />
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
          <Box display="flex" justifyContent="center">
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
};

export default Sidebar;
