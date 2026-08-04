import React, { useState, useEffect, useContext } from 'react';
import { AppBar, IconButton, Toolbar, Drawer, Button, Avatar, useMediaQuery, Box } from '@mui/material';
import { Menu, AccountCircle, Brightness4, Brightness7, AdminPanelSettings } from '@mui/icons-material';
import { Link } from 'react-router-dom';
import { useTheme } from '@mui/material/styles';
import { useDispatch, useSelector } from 'react-redux';

import { ColorModeContext } from '../../utils/ToggleColorMode';
import { setUser, userSelector } from '../../features/auth';
import { Sidebar, Search } from '..';
import { useGetProfileQuery } from '../../services/user';
import { useGetMediaForEntityQuery, getMediaUrl } from '../../services/media';
import { clearAuthTokens } from '../../utils';
import LoginDialog from './LoginDialog';
import useStyles from './styles';

const NavBar = () => {
  const { isAuthenticated, user } = useSelector(userSelector);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
  const classes = useStyles();
  const isMobile = useMediaQuery('(max-width:600px)');
  const theme = useTheme();
  const dispatch = useDispatch();

  const colorMode = useContext(ColorModeContext);

  const hasStoredSession = !!localStorage.getItem('access_token');

  // Restores the redux session from a previously issued JWT on page load,
  // rather than re-authenticating — skipped once the store already has a
  // user (e.g. right after LoginDialog's own setUser dispatch).
  const { data: profile, error: profileError } = useGetProfileQuery(undefined, {
    skip: !hasStoredSession || isAuthenticated,
  });

  const { data: mediaList } = useGetMediaForEntityQuery(
    String(user?.id || ''),
    { skip: !isAuthenticated || !user?.id },
  );

  const userAvatar = mediaList?.find((item) => item.mediaType === 'AVATAR') || mediaList?.[0];
  const avatarUrl = getMediaUrl(userAvatar?.thumbnails?.thumb || userAvatar?.thumbnails?.original);

  useEffect(() => {
    if (profile) {
      dispatch(setUser(profile));
    }
  }, [profile, dispatch]);

  useEffect(() => {
    // Stored JWT is expired/invalid — drop it so we stop retrying on every mount.
    if (profileError) {
      clearAuthTokens();
    }
  }, [profileError]);

  return (
    <>
      <AppBar position="fixed">
        <Toolbar className={classes.toolbar}>
          {isMobile && (
            <IconButton
              color="inherit"
              edge="start"
              style={{ outline: 'none' }}
              onClick={() => setMobileOpen((prevMobileOpen) => !prevMobileOpen)}
              className={classes.menuButton}
            >
              <Menu />
            </IconButton>
          )}
          <IconButton color="inherit" sx={{ ml: 1 }} onClick={colorMode.toggleColorMode}>
            {theme.palette.mode === 'dark' ? <Brightness7 /> : <Brightness4 />}
          </IconButton>
          {!isMobile && <Search />}
          <div>
            {!isAuthenticated ? (
              <Button color="inherit" onClick={() => setLoginOpen(true)}>
                Login &nbsp; <AccountCircle />
              </Button>
            ) : (
              <Box display="flex" alignItems="center">
                {user?.role === 'ADMIN' && (
                  <Button
                    color="inherit"
                    component={Link}
                    to="/admin"
                    className={classes.linkButton}
                    startIcon={<AdminPanelSettings />}
                  >
                    {!isMobile && 'Admin'}
                  </Button>
                )}
                <Button
                  color="inherit"
                  component={Link}
                  to={`/profile/${user.id}`}
                  className={classes.linkButton}
                >
                  {!isMobile && <>My Movies &nbsp;</>}
                  <Avatar
                    data-testid="navbar-avatar"
                    data-src={avatarUrl || ''}
                    style={{ width: 30, height: 30 }}
                    alt={user?.username}
                    src={avatarUrl}
                  >
                    {user?.username?.[0]?.toUpperCase()}
                  </Avatar>
                </Button>
              </Box>
            )}
          </div>
          {isMobile && <Search />}
        </Toolbar>
      </AppBar>
      <div>
        <nav className={classes.drawer}>
          {isMobile ? (
            <Drawer
              variant="temporary"
              anchor="right"
              open={mobileOpen}
              onClose={() => setMobileOpen((prevMobileOpen) => !prevMobileOpen)}
              classes={{ paper: classes.drawerPaper }}
              ModalProps={{ keepMounted: true }}
            >
              <Sidebar setMobileOpen={setMobileOpen} />
            </Drawer>
          ) : (
            <Drawer classes={{ paper: classes.drawerPaper }} variant="permanent" open>
              <Sidebar setMobileOpen={setMobileOpen} />
            </Drawer>
          )}
        </nav>
      </div>
      <LoginDialog open={loginOpen} onClose={() => setLoginOpen(false)} />
    </>
  );
};

export default NavBar;
