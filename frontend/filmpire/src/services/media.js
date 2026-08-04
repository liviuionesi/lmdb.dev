import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

const filmpireApiUrl = process.env.REACT_APP_API_URL || 'http://localhost:8080';

export const getMediaUrl = (url) => {
  if (!url) return null;
  return url.startsWith('/') ? `${filmpireApiUrl}${url}` : url;
};

export const mediaApi = createApi({
  reducerPath: 'mediaApi',
  baseQuery: fetchBaseQuery({
    baseUrl: `${filmpireApiUrl}/api/v1`,
    prepareHeaders: (headers) => {
      const accessToken = localStorage.getItem('access_token');
      if (accessToken) {
        headers.set('Authorization', `Bearer ${accessToken}`);
      }
      return headers;
    },
  }),
  tagTypes: ['Media'],
  endpoints: (builder) => ({
    uploadMedia: builder.mutation({
      query: ({ file, entityId = 'general', entityType = 'USER', mediaType = 'IMAGE', uploadedBy = 'anonymous', description }) => {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('entityId', entityId);
        formData.append('entityType', entityType);
        formData.append('mediaType', mediaType);
        formData.append('uploadedBy', uploadedBy);
        if (description) {
          formData.append('description', description);
        }

        return {
          url: '/media/upload',
          method: 'POST',
          body: formData,
        };
      },
      invalidatesTags: ['Media'],
    }),
    getMediaForEntity: builder.query({
      query: (entityId) => `/media/entity/${entityId}`,
      providesTags: ['Media'],
    }),
    deleteMedia: builder.mutation({
      query: (id) => ({
        url: `/media/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Media'],
    }),
  }),
});

export const {
  useUploadMediaMutation,
  useGetMediaForEntityQuery,
  useDeleteMediaMutation,
} = mediaApi;
