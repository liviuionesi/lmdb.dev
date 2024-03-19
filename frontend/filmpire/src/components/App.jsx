import React from 'react';
import { CssBaseline } from '@mui/material';
import { Route, Switch } from 'react-router-dom';

const App = () => (
  <div>
    <CssBaseline />
    <main>
      <Switch>
        <Route exact path="/"><h1>Movies</h1></Route>
        <Route exact path="/movies/:id"><h1>Movie information</h1></Route>
        <Route exact path="/actors/:id"><h1>Actors</h1></Route>
        <Route exact path="/profiles/:id"><h1>Profile</h1></Route>
      </Switch>
    </main>
  </div>
);

export default App;
