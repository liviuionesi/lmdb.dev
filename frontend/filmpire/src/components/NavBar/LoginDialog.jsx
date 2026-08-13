import React, { useState } from 'react';
import { Alert, Box, Button, Dialog, DialogContent, DialogTitle, Tab, Tabs, TextField } from '@mui/material';
import { useDispatch } from 'react-redux';

import { useLoginMutation, useRegisterMutation } from '../../services/user';
import { setUser } from '../../features/auth';
import { storeAuthTokens } from '../../utils';

/**
 * Login/register modal backed by Filmpire's own user-service JWT API
 * (replaces the old redirect-to-themoviedb.org flow).
 */
function LoginDialog({ open, onClose }) {
  const dispatch = useDispatch();
  const [tab, setTab] = useState('login');
  const [form, setForm] = useState({ username: '', email: '', password: '' });

  const [login, { isLoading: isLoggingIn, error: loginError }] = useLoginMutation();
  const [register, { isLoading: isRegistering, error: registerError }] = useRegisterMutation();

  const isSubmitting = isLoggingIn || isRegistering;
  const error = tab === 'login' ? loginError : registerError;

  const handleChange = (field) => (event) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }));
  };

  const handleClose = () => {
    setForm({ username: '', email: '', password: '' });
    onClose();
  };

  const handleAuthenticated = (auth) => {
    storeAuthTokens(auth);
    dispatch(setUser(auth.user));
    handleClose();
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (tab === 'login') {
      const result = await login({ username: form.username, password: form.password });
      if (result.data) {
        handleAuthenticated(result.data);
      }
    } else {
      const result = await register(form);
      if (result.data) {
        handleAuthenticated(result.data);
      }
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="xs">
      <DialogTitle>LIMDb Account</DialogTitle>
      <Tabs value={tab} onChange={(event, value) => setTab(value)} variant="fullWidth">
        <Tab label="Login" value="login" />
        <Tab label="Register" value="register" />
      </Tabs>
      <DialogContent>
        <Box
          component="form"
          onSubmit={handleSubmit}
          sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
            pt: 1,
          }}
        >
          {error && (
            <Alert severity="error">
              {error.data?.message || 'Something went wrong. Please try again.'}
            </Alert>
          )}
          <TextField
            label="Username"
            value={form.username}
            onChange={handleChange('username')}
            required
            autoFocus
          />
          {tab === 'register' && (
            <TextField
              label="Email"
              type="email"
              value={form.email}
              onChange={handleChange('email')}
              required
            />
          )}
          <TextField
            label="Password"
            type="password"
            value={form.password}
            onChange={handleChange('password')}
            required
          />
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            {tab === 'login' ? 'Login' : 'Create account'}
          </Button>
        </Box>
      </DialogContent>
    </Dialog>
  );
}

export default LoginDialog;
