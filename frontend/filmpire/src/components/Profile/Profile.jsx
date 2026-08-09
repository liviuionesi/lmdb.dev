import React, { useState } from 'react';
import { Typography, Button, Box, Avatar, CircularProgress, Alert } from '@mui/material';
import { useDispatch, useSelector } from 'react-redux';
import { ExitToApp, PhotoCamera } from '@mui/icons-material';
import { Navigate } from 'react-router-dom';

import { useGetFavoritesQuery, useGetWatchlistQuery, useLogoutMutation } from '../../services/user';
import { useGetMediaForEntityQuery, useUploadMediaMutation, getMediaUrl } from '../../services/media';
import { clearUser, userSelector } from '../../features/auth';
import { clearAuthTokens } from '../../utils';
import { RatedCards } from '..';

function Profile() {
  const { isAuthenticated, user } = useSelector(userSelector);
  const dispatch = useDispatch();
  const [logout] = useLogoutMutation();

  const [validationError, setValidationError] = useState('');
  const [uploadSuccess, setUploadSuccess] = useState(false);

  const { data: favorites } = useGetFavoritesQuery(undefined, { skip: !isAuthenticated });
  const { data: watchlist } = useGetWatchlistQuery(undefined, { skip: !isAuthenticated });

  const profileEntityId = String(user?.id || user?.username || 'user');
  const { data: mediaList, refetch: refetchMedia } = useGetMediaForEntityQuery(
    profileEntityId,
    { skip: !isAuthenticated },
  );
  const [uploadMedia, { isLoading: isUploading }] = useUploadMediaMutation();

  if (!isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  const favoriteIds = favorites?.map((entry) => entry.movieId) ?? [];
  const watchlistIds = watchlist?.map((entry) => entry.movieId) ?? [];

  // Pick the most recently uploaded AVATAR asset, falling back to the most
  // recent media item of any type. `mediaList && mediaList.length > 0` (rather
  // than `mediaList?.length - 1`) keeps the length check and the arithmetic on
  // it separate, since ESLint's no-unsafe-optional-chaining flags arithmetic
  // directly on an optional-chained value (it can silently evaluate to NaN).
  const avatarList = mediaList?.filter((item) => item.mediaType === 'AVATAR' || item.entityType === 'USER') || [];
  const lastMediaItem = mediaList && mediaList.length > 0 ? mediaList[mediaList.length - 1] : undefined;
  const userAvatar = avatarList.length > 0 ? avatarList[avatarList.length - 1] : lastMediaItem;
  const rawAvatarUrl = getMediaUrl(userAvatar?.thumbnails?.medium || userAvatar?.thumbnails?.original);
  const avatarSeparator = rawAvatarUrl?.includes('?') ? '&' : '?';
  const avatarTimestamp = new Date(userAvatar?.uploadedAt || Date.now()).getTime();
  const avatarUrl = rawAvatarUrl ? `${rawAvatarUrl}${avatarSeparator}t=${avatarTimestamp}` : '';

  const handleFileChange = async (e) => {
    setValidationError('');
    setUploadSuccess(false);
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.match(/^image\/(jpeg|png)$/)) {
      setValidationError('Only JPG and PNG images are supported for avatar upload.');
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      setValidationError('File size exceeds the 5MB maximum limit.');
      return;
    }

    try {
      await uploadMedia({
        file,
        entityId: profileEntityId,
        entityType: 'USER',
        mediaType: 'AVATAR',
        uploadedBy: user?.username || 'anonymous',
      }).unwrap();
      setUploadSuccess(true);
      refetchMedia();
    } catch (err) {
      setValidationError(err?.data?.message || 'Failed to upload photo to server. Please try again.');
    }
  };

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
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          mb: 4,
        }}
      >
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 3,
          }}
        >
          <Box sx={{
            position: 'relative',
          }}
          >
            <Avatar
              data-testid="user-avatar"
              data-src={avatarUrl || ''}
              src={avatarUrl}
              alt={user?.username}
              sx={{ width: 80, height: 80, fontSize: '2rem' }}
            >
              {user?.username?.[0]?.toUpperCase()}
            </Avatar>
            {isUploading && (
              <CircularProgress
                size={86}
                sx={{
                  position: 'absolute',
                  top: -3,
                  left: -3,
                  zIndex: 1,
                }}
              />
            )}
          </Box>
          <Box>
            <Typography variant="h4" gutterBottom>{user?.username || 'My Profile'}</Typography>
            <Button
              variant="outlined"
              component="label"
              size="small"
              startIcon={<PhotoCamera />}
              disabled={isUploading}
            >
              <span>Upload Avatar</span>
              <input
                type="file"
                hidden
                accept="image/jpeg,image/png"
                onChange={handleFileChange}
                data-testid="avatar-upload-input"
              />
            </Button>
          </Box>
        </Box>
        <Button color="inherit" onClick={handleLogout}>
          Logout &nbsp; <ExitToApp />
        </Button>
      </Box>
      {validationError && (
        <Box sx={{
          mb: 3,
        }}
        >
          <Alert severity="error" onClose={() => setValidationError('')}>{validationError}</Alert>
        </Box>
      )}
      {uploadSuccess && (
        <Box sx={{
          mb: 3,
        }}
        >
          <Alert severity="success" onClose={() => setUploadSuccess(false)}>Avatar updated successfully!</Alert>
        </Box>
      )}
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
}

export default Profile;
