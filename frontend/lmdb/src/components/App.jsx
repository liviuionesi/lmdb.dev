import React from 'react';
import { CssBaseline } from '@mui/material';
import { Route, Routes } from 'react-router-dom';
import { useDispatch } from 'react-redux';

import useStyles from './styles';
import VoiceControl from './VoiceControl/VoiceControl';
import { tmdbApi } from '../services/TMDB';

import {
  About,
  Actors,
  AdminDashboard,
  BackendStandbyModal,
  Footer,
  MovieInformation,
  Movies,
  NavBar,
  Profile,
  Recommendations,
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
          <Route path="/about" element={<About />} />
          <Route path="/" element={<Movies />} />
          <Route path="/profile/:id" element={<Profile />} />
          <Route path="/recommendations" element={<Recommendations />} />
        </Routes>
        <Footer />
      </main>
      <BackendStandbyModal onBackendReady={handleBackendReady} />
      <VoiceControl />
    </div>
  );
}

export default App;
