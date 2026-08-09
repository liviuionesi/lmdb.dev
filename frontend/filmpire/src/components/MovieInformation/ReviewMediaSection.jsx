import React, { useState } from 'react';
import { Box, Typography, Button, TextField, Grid, Modal, Alert, CircularProgress, Card, CardMedia, IconButton } from '@mui/material';
// MUI 9 removed the bare `PlayCircleOutline` export (deduped against the
// identical SVG path already exposed as `PlayCircleOutlineOutlined`) — see #135.
import { AttachFile, PlayCircleOutlineOutlined as PlayCircleOutline, Send, Close } from '@mui/icons-material';
import { useSelector } from 'react-redux';
import { userSelector } from '../../features/auth';
import { useGetMediaForEntityQuery, useUploadMediaMutation, getMediaUrl } from '../../services/media';

const ReviewMediaSection = ({ movieId }) => {
  const { isAuthenticated, user } = useSelector(userSelector);
  const [comment, setComment] = useState('');
  const [selectedFile, setSelectedFile] = useState(null);
  const [validationError, setValidationError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [selectedLightboxItem, setSelectedLightboxItem] = useState(null);

  const { data: mediaList, refetch, isFetching } = useGetMediaForEntityQuery(String(movieId));
  const [uploadMedia, { isLoading: isUploading }] = useUploadMediaMutation();

  const handleFileSelect = (e) => {
    setValidationError('');
    setSuccessMessage('');
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/') && !file.type.startsWith('video/')) {
      setValidationError('Only image (JPG/PNG) and video (MP4/MOV/WEBM) formats are allowed for attachments.');
      setSelectedFile(null);
      return;
    }

    if (file.size > 20 * 1024 * 1024) {
      setValidationError('Attachment file size exceeds the 20MB maximum allowance.');
      setSelectedFile(null);
      return;
    }

    setSelectedFile(file);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setValidationError('');
    setSuccessMessage('');

    if (!selectedFile && !comment.trim()) {
      setValidationError('Please provide a review comment or attach a media proof file before submitting.');
      return;
    }

    try {
      if (selectedFile) {
        const mediaType = selectedFile.type.startsWith('video/') ? 'VIDEO' : 'IMAGE';
        await uploadMedia({
          file: selectedFile,
          entityId: String(movieId),
          entityType: 'MOVIE_REVIEW',
          mediaType,
          uploadedBy: user?.username || 'anonymous',
          description: comment.trim() || undefined,
        }).unwrap();
      } else {
        // Text-only review: upload a tiny placeholder so we can store the description
        const blob = new Blob([comment.trim()], { type: 'text/plain' });
        const placeholderFile = new File([blob], 'review.txt', { type: 'text/plain' });
        await uploadMedia({
          file: placeholderFile,
          entityId: String(movieId),
          entityType: 'MOVIE_REVIEW',
          mediaType: 'ATTACHMENT',
          uploadedBy: user?.username || 'anonymous',
          description: comment.trim(),
        }).unwrap();
      }
      setSuccessMessage('Review submitted successfully!');
      setComment('');
      setSelectedFile(null);
      refetch();
    } catch (err) {
      setValidationError(err?.data?.message || 'Failed to submit review attachment to media service. Please try again.');
    }
  };

  return (
    <Box
      sx={{
        marginTop: '4rem',
        width: '100%',
      }}
    >
      <Typography variant="h4" gutterBottom>
        Fan Reviews & Proof Gallery
      </Typography>
      <Typography variant="subtitle1" color="textSecondary" sx={{ marginBottom: '16px' }}>
        Share screenshot reactions, easter eggs, or video review clips with the community.
      </Typography>

      {isAuthenticated ? (
        <Box
          component="form"
          onSubmit={handleSubmit}
          sx={{
            p: 3,
            mb: 4,
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 2,
            backgroundColor: 'background.paper',
          }}
        >
          <TextField
            fullWidth
            multiline
            rows={2}
            label="Write your movie thoughts or attachment note..."
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            disabled={isUploading}
            sx={{ mb: 2 }}
          />
          <Box
            sx={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: 2,
            }}
          >
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 2,
              }}
            >
              <Button
                variant="outlined"
                component="label"
                size="small"
                startIcon={<AttachFile />}
                disabled={isUploading}
              >
                <span>Attach Clip or Screenshot</span>
                <input
                  type="file"
                  hidden
                  accept="image/*,video/*"
                  onChange={handleFileSelect}
                  data-testid="review-attachment-input"
                />
              </Button>
              {selectedFile && (
                <Typography variant="body2" color="primary">
                  Attached: {selectedFile.name}
                </Typography>
              )}
            </Box>
            <Button
              type="submit"
              variant="contained"
              color="primary"
              endIcon={isUploading ? <CircularProgress size={18} color="inherit" /> : <Send />}
              disabled={isUploading || (!comment.trim() && !selectedFile)}
            >
              Submit Review
            </Button>
          </Box>
          {validationError && (
            <Box sx={{
              mt: 2,
            }}
            >
              <Alert severity="error" onClose={() => setValidationError('')}>{validationError}</Alert>
            </Box>
          )}
          {successMessage && (
            <Box sx={{
              mt: 2,
            }}
            >
              <Alert severity="success" onClose={() => setSuccessMessage('')}>{successMessage}</Alert>
            </Box>
          )}
        </Box>
      ) : (
        <Alert severity="info" sx={{ mb: 4 }}>
          Please login to upload your own screenshot reactions or video review clips.
        </Alert>
      )}

      {isFetching && (
        <Box
          sx={{
            display: 'flex',
            justifyContent: 'center',
            my: 3,
          }}
        >
          <CircularProgress />
        </Box>
      )}

      {mediaList && mediaList.length > 0 ? (
        <Grid container spacing={2}>
          {mediaList.map((media) => {
            const isVideo = media.mediaType === 'VIDEO' || media.mimeType?.startsWith('video/');
            const isTextOnly = media.mediaType === 'ATTACHMENT' && media.mimeType === 'text/plain';
            const displayUrl = getMediaUrl(media.thumbnails?.medium || media.thumbnails?.original);
            return (
              <Grid
                key={media.id || media.storagePath}
                size={{
                  xs: 12,
                  sm: isTextOnly ? 12 : 6,
                  md: isTextOnly ? 12 : 4,
                }}
              >
                <Card
                  sx={{
                    cursor: isTextOnly ? 'default' : 'pointer',
                    transition: 'transform 0.2s',
                    '&:hover': { transform: isTextOnly ? 'none' : 'scale(1.03)' },
                    position: 'relative',
                    backgroundColor: 'background.paper',
                    border: '1px solid',
                    borderColor: 'divider',
                  }}
                  onClick={() => !isTextOnly && setSelectedLightboxItem(media)}
                  data-testid={`gallery-item-${media.id || 'default'}`}
                >
                  {isTextOnly ? (
                    <Box sx={{
                      p: 2,
                    }}
                    >
                      <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap', mb: 1 }}>
                        {media.description || media.originalFilename}
                      </Typography>
                      <Typography variant="caption" color="textSecondary">
                        By @{media.uploadedBy || 'anonymous'}
                      </Typography>
                    </Box>
                  ) : (
                    <Box sx={{ height: 180 }}>
                      {!isVideo ? (
                        <CardMedia
                          component="img"
                          height="180"
                          image={displayUrl}
                          alt={media.originalFilename || 'Review media'}
                          sx={{ objectFit: 'cover' }}
                        />
                      ) : (
                        <Box
                          sx={{
                            height: '180',
                            display: 'flex',
                            flexDirection: 'column',
                            justifyContent: 'center',
                            alignItems: 'center',
                            color: 'white',
                          }}
                        >
                          <PlayCircleOutline sx={{ fontSize: 48, mb: 1 }} />
                          <Typography variant="caption" noWrap sx={{ px: 1, maxWidth: '90%' }}>
                            {media.originalFilename || 'Video Clip'}
                          </Typography>
                        </Box>
                      )}
                      <Box
                        sx={{
                          position: 'absolute',
                          bottom: 0,
                          width: '100%',
                          bgcolor: 'rgba(0, 0, 0, 0.6)',
                          p: 0.5,
                        }}
                      >
                        {media.description && (
                          <Typography
                            variant="caption"
                            noWrap
                            align="center"
                            sx={{
                              display: 'block',
                              color: '#dddddd',
                            }}
                          >
                            {media.description}
                          </Typography>
                        )}
                        <Typography
                          variant="caption"
                          noWrap
                          align="center"
                          sx={{
                            display: 'block',
                            color: '#ffffff',
                          }}
                        >
                          By @{media.uploadedBy || 'anonymous'}
                        </Typography>
                      </Box>
                    </Box>
                  )}
                </Card>
              </Grid>
            );
          })}
        </Grid>
      ) : (
        <Box
          sx={{
            textAlign: 'center',
            py: 3,
            color: 'textSecondary',
          }}
        >
          <Typography variant="body1">No review attachments uploaded for this movie yet. Be the first!</Typography>
        </Box>
      )}

      <Modal
        open={!!selectedLightboxItem}
        onClose={() => setSelectedLightboxItem(null)}
        sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', p: 2 }}
        data-testid="lightbox-modal"
      >
        <Box
          sx={{
            position: 'relative',
            bgcolor: 'background.paper',
            borderRadius: 2,
            p: 2,
            boxShadow: 24,
            maxWidth: '90vw',
            maxHeight: '90vh',
          }}
        >
          <IconButton
            onClick={() => setSelectedLightboxItem(null)}
            sx={{ position: 'absolute', right: 8, top: 8, zIndex: 10, bgcolor: 'background.paper' }}
            aria-label="close-lightbox"
          >
            <Close />
          </IconButton>
          {selectedLightboxItem && (
            <Box
              sx={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                pt: 3,
              }}
            >
              {selectedLightboxItem.mediaType === 'VIDEO' || selectedLightboxItem.mimeType?.startsWith('video/') ? (
                <video
                  controls
                  autoPlay
                  style={{ maxWidth: '85vw', maxHeight: '75vh', borderRadius: 8 }}
                  src={getMediaUrl(selectedLightboxItem.thumbnails?.original)}
                  data-testid="lightbox-video"
                >
                  <track kind="captions" />
                </video>
              ) : (
                <img
                  style={{ maxWidth: '85vw', maxHeight: '75vh', borderRadius: 8, objectFit: 'contain' }}
                  src={getMediaUrl(selectedLightboxItem.thumbnails?.original)}
                  alt="Full resolution preview"
                  data-testid="lightbox-image"
                />
              )}
              <Typography variant="subtitle2" sx={{ mt: 1 }}>
                Uploaded by @{selectedLightboxItem.uploadedBy || 'anonymous'}
              </Typography>
            </Box>
          )}
        </Box>
      </Modal>
    </Box>
  );
};

export default ReviewMediaSection;
