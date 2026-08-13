// Tests LoginDialog: tab switching, error display, and successful
// login/register flows (tokens stored, user dispatched, dialog closed).
//
// Field values are set with fireEvent.change (one dispatch, whole string)
// rather than userEvent.type (one dispatch per keystroke): LoginDialog's
// onChange uses a functional setState update (`setForm((prev) => ...)`), and
// under React 17 + jsdom, firing many rapid synthetic keystroke events
// against that pattern drops all but the first couple of characters — a
// test-environment quirk, not a real typing bug, that fireEvent.change sidesteps.
import React from 'react';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { configureStore } from '@reduxjs/toolkit';

import LoginDialog from './LoginDialog';
import authReducer from '../../features/auth';
import { renderWithProviders } from '../../test-utils/render';
import { useLoginMutation, useRegisterMutation } from '../../services/user';

vi.mock('../../services/user', () => ({
  useLoginMutation: vi.fn(),
  useRegisterMutation: vi.fn(),
}));

const buildStore = () => configureStore({ reducer: { user: authReducer } });

const setValue = (element, value) => fireEvent.change(element, { target: { value } });

describe('LoginDialog', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('renders nothing meaningful when closed (Dialog unmounts its content)', () => {
    useLoginMutation.mockReturnValue([vi.fn(), { isLoading: false, error: undefined }]);
    useRegisterMutation.mockReturnValue([vi.fn(), { isLoading: false, error: undefined }]);
    renderWithProviders(<LoginDialog open={false} onClose={() => {}} />, { store: buildStore() });

    expect(screen.queryByText('LIMDb Account')).not.toBeInTheDocument();
  });

  it('shows the Register fields only after switching tabs', async () => {
    useLoginMutation.mockReturnValue([vi.fn(), { isLoading: false, error: undefined }]);
    useRegisterMutation.mockReturnValue([vi.fn(), { isLoading: false, error: undefined }]);
    renderWithProviders(<LoginDialog open onClose={() => {}} />, { store: buildStore() });

    expect(screen.queryByLabelText(/^email/i)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('tab', { name: 'Register' }));

    expect(screen.getByLabelText(/^email/i)).toBeInTheDocument();
  });

  it('shows the login error message when the login mutation fails', () => {
    useLoginMutation.mockReturnValue([vi.fn(), { isLoading: false, error: { data: { message: 'Bad credentials' } } }]);
    useRegisterMutation.mockReturnValue([vi.fn(), { isLoading: false, error: undefined }]);
    renderWithProviders(<LoginDialog open onClose={() => {}} />, { store: buildStore() });

    expect(screen.getByText('Bad credentials')).toBeInTheDocument();
  });

  it('shows a generic error message when the error has no message field', () => {
    useLoginMutation.mockReturnValue([vi.fn(), { isLoading: false, error: {} }]);
    useRegisterMutation.mockReturnValue([vi.fn(), { isLoading: false, error: undefined }]);
    renderWithProviders(<LoginDialog open onClose={() => {}} />, { store: buildStore() });

    expect(screen.getByText('Something went wrong. Please try again.')).toBeInTheDocument();
  });

  it('logs in, stores tokens, dispatches the user, and closes the dialog on success', async () => {
    const login = vi.fn().mockResolvedValue({ data: { accessToken: 'a', refreshToken: 'r', user: { id: 1, username: 'liviu' } } });
    useLoginMutation.mockReturnValue([login, { isLoading: false, error: undefined }]);
    useRegisterMutation.mockReturnValue([vi.fn(), { isLoading: false, error: undefined }]);
    const onClose = vi.fn();
    const store = buildStore();
    renderWithProviders(<LoginDialog open onClose={onClose} />, { store });

    setValue(screen.getByLabelText(/^username/i), 'liviu');
    setValue(screen.getByLabelText(/^password/i), 'secret');
    await userEvent.click(screen.getByRole('button', { name: 'Login' }));

    expect(login).toHaveBeenCalledWith({ username: 'liviu', password: 'secret' });
    await waitFor(() => expect(localStorage.getItem('access_token')).toBe('a'));
    expect(store.getState().user.isAuthenticated).toBe(true);
    expect(onClose).toHaveBeenCalled();
  });

  it('registers a new account when submitted from the Register tab', async () => {
    const register = vi.fn().mockResolvedValue({ data: { accessToken: 'a', refreshToken: 'r', user: { id: 2, username: 'newbie' } } });
    useLoginMutation.mockReturnValue([vi.fn(), { isLoading: false, error: undefined }]);
    useRegisterMutation.mockReturnValue([register, { isLoading: false, error: undefined }]);
    renderWithProviders(<LoginDialog open onClose={() => {}} />, { store: buildStore() });

    await userEvent.click(screen.getByRole('tab', { name: 'Register' }));
    setValue(screen.getByLabelText(/^username/i), 'newbie');
    setValue(screen.getByLabelText(/^email/i), 'n@example.com');
    setValue(screen.getByLabelText(/^password/i), 'secret');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(register).toHaveBeenCalledWith({ username: 'newbie', email: 'n@example.com', password: 'secret' });
  });

  it('does not authenticate when the login mutation returns no data', async () => {
    const login = vi.fn().mockResolvedValue({ error: { status: 401 } });
    useLoginMutation.mockReturnValue([login, { isLoading: false, error: undefined }]);
    useRegisterMutation.mockReturnValue([vi.fn(), { isLoading: false, error: undefined }]);
    const onClose = vi.fn();
    const store = buildStore();
    renderWithProviders(<LoginDialog open onClose={onClose} />, { store });

    setValue(screen.getByLabelText(/^username/i), 'liviu');
    setValue(screen.getByLabelText(/^password/i), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Login' }));

    expect(store.getState().user.isAuthenticated).toBe(false);
    expect(onClose).not.toHaveBeenCalled();
  });
});
