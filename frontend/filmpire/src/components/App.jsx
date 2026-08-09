import React from 'react';
import { CssBaseline } from '@mui/material';
import { Route, Routes } from 'react-router-dom';

import useStyles from './styles';
import VoiceControl from './VoiceControl/VoiceControl';

import { Actors, AdminDashboard, MovieInformation, Movies, NavBar, Profile } from '.';

function App() {
  const { classes } = useStyles();

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
      </main>
      <VoiceControl />
    </div>
  );
}

export default App;
