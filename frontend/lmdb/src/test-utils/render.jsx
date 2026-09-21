// Shared RTL render helper: every component in this app relies on
// tss-react/mui's makeStyles (#130), which reads its theme from
// @mui/material/styles' ThemeProvider (jsdom has none by default, so
// makeStyles throws on `theme.breakpoints` without it) and several
// components use react-router-dom's Link/useParams/useLocation, so a
// MemoryRouter is always supplied too. A redux Provider is
// only added when a test passes a `store` (presentational components that
// don't touch redux skip it entirely). Passing `path` (e.g. "/actors/:id")
// wraps the element in a matching <Routes><Route element={...} /></Routes>
// so useParams() resolves — without it, a component rendered bare inside
// MemoryRouter sees empty params.
import React from 'react';
import { render } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { ColorModeContext } from '../utils/ToggleColorMode';

export const theme = createTheme();

export const renderWithProviders = (ui, { route = '/', path, store, ...renderOptions } = {}) => {
  const toggleColorMode = () => {};
  let element = <ColorModeContext.Provider value={{ setMode: () => {}, toggleColorMode }}><ThemeProvider theme={theme}>{ui}</ThemeProvider></ColorModeContext.Provider>;
  element = path ? <Routes><Route path={path} element={element} /></Routes> : element;
  element = <MemoryRouter initialEntries={[route]}>{element}</MemoryRouter>;
  if (store) {
    element = <Provider store={store}>{element}</Provider>;
  }
  return render(element, renderOptions);
};
