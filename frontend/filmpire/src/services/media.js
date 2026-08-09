import { createApi } from '@reduxjs/toolkit/query/react';
import { getApiUrl, createDynamicBaseQuery } from '../utils/apiUrl';

export const getMediaUrl = (url) => {
  if (!url) return null;
  return url.startsWith('/') ? `${getApiUrl()}${url}` : url;
};

export const buildUploadMediaQuery = ({
  file,
  entityId = 'general',
  entityType = 'USER',
  mediaType = 'IMAGE',
  uploadedBy = 'anonymous',
  description,
}) => {
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
};

export const mediaApi = createApi({
  reducerPath: 'mediaApi',
  baseQuery: createDynamicBaseQuery('/api/v1'),
  tagTypes: ['Media'],
  endpoints: (builder) => ({
    uploadMedia: builder.mutation({
      query: buildUploadMediaQuery,
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
