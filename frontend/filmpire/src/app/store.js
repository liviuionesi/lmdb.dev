import { configureStore } from '@reduxjs/toolkit';

import { tmdbApi } from '../services/TMDB';
import { userApi } from '../services/user';
import { mediaApi } from '../services/media';
import genreOrCategoryReducer from '../features/currentGenreOrCategory';
import userReducer from '../features/auth';

export default configureStore({
  reducer: {
    [tmdbApi.reducerPath]: tmdbApi.reducer,
    [userApi.reducerPath]: userApi.reducer,
    [mediaApi.reducerPath]: mediaApi.reducer,
    currentGenreOrCategory: genreOrCategoryReducer,
    user: userReducer,
  },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(
    tmdbApi.middleware,
    userApi.middleware,
    mediaApi.middleware,
  ),
});
