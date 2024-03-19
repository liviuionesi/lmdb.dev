import React from 'react';
import { CssBaseline } from '@mui/material';
import { Route, Switch } from 'react-router-dom';
import { Actors, MovieInformation, Movies, Profile, Navbar } from '.';

const App = () => (
  <div>
    <CssBaseline />
    <Navbar />
    <main>
      <Switch>
        <Route exact path="/"><Movies /></Route>
        <Route exact path="/movies/:id"><MovieInformation /></Route>
        <Route exact path="/actors/:id"><Actors /></Route>
        <Route exact path="/profiles/:id"><Profile /></Route>
      </Switch>
    </main>
  </div>
);

export default App;
