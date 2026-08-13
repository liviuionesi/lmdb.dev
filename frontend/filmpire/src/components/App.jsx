import React from 'react';
import { CssBaseline } from '@mui/material';
import { Route, Routes } from 'react-router-dom';
import { useDispatch } from 'react-redux';

import useStyles from './styles';
import VoiceControl from './VoiceControl/VoiceControl';
import { tmdbApi } from '../services/TMDB';

import {
  Actors,
  AdminDashboard,
  BackendStandbyModal,
  Footer,
  MovieInformation,
  Movies,
  NavBar,
  Profile,
} from '.';

function App() {
  const { classes } = useStyles();
  const dispatch = useDispatch();

  const handleBackendReady = () => {
    // Automatically re-fetch all active movie/user queries once backend goes live
    dispatch(tmdbApi.util.resetApiState());
  };

  return (
    <div className={classes.root}>
      <CssBaseline />
      <NavBar />
      <main className={classes.content}>
        <div className={classes.toolbar} />
        <Routes>
          <Route path="/movie/:id" element={<MovieInformation />} />
          <Route path="/actors/:id" element={<Actors />} />
          <Route path="/admin" element={<AdminDashboard />} />
          <Route path="/" element={<Movies />} />
          <Route path="/profile/:id" element={<Profile />} />
        </Routes>
        <Footer />
      </main>
      <BackendStandbyModal onBackendReady={handleBackendReady} />
      <VoiceControl />
    </div>
  );
}

export default App;
