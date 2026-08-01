import React from 'react';
import { Typography, Button, Box } from '@mui/material';
import { useDispatch, useSelector } from 'react-redux';
import { ExitToApp } from '@mui/icons-material';

import { useGetFavoritesQuery, useGetWatchlistQuery, useLogoutMutation } from '../../services/user';
import { clearUser, userSelector } from '../../features/auth';
import { clearAuthTokens } from '../../utils';
import { RatedCards } from '..';

const Profile = () => {
  const { isAuthenticated } = useSelector(userSelector);
  const dispatch = useDispatch();
  const [logout] = useLogoutMutation();

  const { data: favorites } = useGetFavoritesQuery(undefined, { skip: !isAuthenticated });
  const { data: watchlist } = useGetWatchlistQuery(undefined, { skip: !isAuthenticated });

  const favoriteIds = favorites?.map((entry) => entry.movieId) ?? [];
  const watchlistIds = watchlist?.map((entry) => entry.movieId) ?? [];

  const handleLogout = async () => {
    // Best-effort — the JWTs are cleared locally regardless of whether the
    // revoke call itself succeeds.
    await logout().catch(() => {});
    clearAuthTokens();
    dispatch(clearUser());
    window.location.href = '/';
  };

  return (
    <Box>
      <Box display="flex" justifyContent="space-between">
        <Typography variant="h4" gutterBottom>My Profile</Typography>
        <Button color="inherit" onClick={handleLogout}>
          Logout &nbsp; <ExitToApp />
        </Button>
      </Box>
      {!favoriteIds.length && !watchlistIds.length
        ? <Typography variant="h5">Add favorites or watchlist some movies to see them here!</Typography>
        : (
          <Box>
            <RatedCards title="Favorite Movies" movieIds={favoriteIds} />
            <RatedCards title="Watchlist" movieIds={watchlistIds} />
          </Box>
        )}
    </Box>
  );
};

export default Profile;
