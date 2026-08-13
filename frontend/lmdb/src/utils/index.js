//* Reads/writes the JWT pair issued by user-service's /api/v1/auth endpoints.
export const storeAuthTokens = ({ accessToken, refreshToken }) => {
  localStorage.setItem('access_token', accessToken);
  localStorage.setItem('refresh_token', refreshToken);
};

export const clearAuthTokens = () => {
  localStorage.removeItem('access_token');
  localStorage.removeItem('refresh_token');
};
