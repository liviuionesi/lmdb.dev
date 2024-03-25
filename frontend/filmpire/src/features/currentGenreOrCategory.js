import { createSlice } from '@reduxjs/toolkit';

export const genreOrCategory = createSlice({
  name: 'genreOrCategory',
  initialState: {
    genreOrCategoryName: '',
    pageNumber: 1,
    searchQuery: '',
  },
  reducers: {
    selectGenreOrCategory: (state, action) => {
      console.log(action.payload);
      // state.genreOrCategoryName = action.payload;
      // return state;
    },
  },
});

export const { selectGenreOrCategory } = genreOrCategory.actions;

// Every reducer must be attached to the store
export default genreOrCategory.reducer;
