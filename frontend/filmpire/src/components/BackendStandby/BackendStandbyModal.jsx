import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  Box,
  Typography,
  IconButton,
  keyframes,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';

import { useBackendWakeup } from './useBackendWakeup';
import { getStandbyTrailerId } from '../../utils/trailer';
import LIMDbLogo from '../Logo/LIMDbLogo';

const logoGlow = keyframes`
  0%, 100% {
    filter: drop-shadow(0 0 20px rgba(229, 9, 20, 0.7)) drop-shadow(0 0 45px rgba(245, 197, 24, 0.4));
    transform: scale(1);
  }
  50% {
    filter: drop-shadow(0 0 35px rgba(229, 9, 20, 0.95)) drop-shadow(0 0 70px rgba(245, 197, 24, 0.7));
    transform: scale(1.03);
  }
`;

const subtitleFade = keyframes`
  from {
    opacity: 0;
  }
  to {
    opacity: 0.5;
  }
`;

const SUBTITLES = [
  'Welcome to LIMDb Theaters. Getting your movie experience ready...',
  'Preparing top blockbuster trailers and theater collections...',
  'Bringing together thousands of movies, cast spotlights, and reviews...',
  'Dimming the lights. Enjoy the show...',
];

/**
 * Pure Cinematic Movie Trailer Standby Experience.
 * User-oriented announcements with 50% transparency, positioned at the top of the trailer,
 * with no border, no background, and no icons, disappearing after 5 seconds each.
 *
 * @param {Object} props
 * @param {Function} [props.onBackendReady] - Callback triggered when the backend is live
 */
function BackendStandbyModal({ onBackendReady }) {
  const [open, setOpen] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const [showLogoReveal, setShowLogoReveal] = useState(false);
  const [trailerId] = useState(() => getStandbyTrailerId());
  const [subtitleIndex, setSubtitleIndex] = useState(0);

  const { status } = useBackendWakeup({
    autoWakeup: true,
    onReady: () => {
      // Trigger dramatic Filmpire logo reveal before final dismissal
      setShowLogoReveal(true);
      if (onBackendReady) {
        onBackendReady();
      }
      setTimeout(() => {
        setOpen(false);
        setShowLogoReveal(false);
      }, 2500);
    },
  });

  useEffect(() => {
    if ((status === 'STANDBY' || status === 'WAKING_UP') && !dismissed) {
      setOpen(true);
    } else if (status === 'ONLINE' && !showLogoReveal) {
      setOpen(false);
    }
  }, [status, dismissed]);

  // Advance each subtitle every 5 seconds, disappearing once all have played
  useEffect(() => {
    if (!open || subtitleIndex >= SUBTITLES.length) return undefined;

    const timer = setTimeout(() => {
      setSubtitleIndex((prev) => prev + 1);
    }, 5000);

    return () => clearTimeout(timer);
  }, [open, subtitleIndex]);

  const handleClose = () => {
    setDismissed(true);
    setOpen(false);
    setShowLogoReveal(false);
  };

  if (!open) {
    return null;
  }

  const currentSubtitle = subtitleIndex < SUBTITLES.length ? SUBTITLES[subtitleIndex] : null;

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      fullWidth
      maxWidth="md"
      slotProps={{
        paper: {
          sx: {
            bgcolor: '#000000',
            color: '#ffffff',
            borderRadius: 3,
            border: '1px solid rgba(245, 197, 24, 0.25)',
            overflow: 'hidden',
            boxShadow: '0 25px 90px rgba(0, 0, 0, 0.98), 0 0 60px rgba(229, 9, 20, 0.35)',
            position: 'relative',
          },
        },
      }}
    >
      {/* Floating Top-Right Close Button */}
      <IconButton
        aria-label="close"
        onClick={handleClose}
        size="small"
        sx={{
          position: 'absolute',
          top: 12,
          right: 12,
          zIndex: 10,
          color: 'rgba(255,255,255,0.7)',
          bgcolor: 'rgba(0,0,0,0.6)',
          backdropFilter: 'blur(4px)',
          border: '1px solid rgba(255,255,255,0.15)',
          '&:hover': { bgcolor: 'rgba(0,0,0,0.85)', color: '#ffffff' },
        }}
      >
        <CloseIcon fontSize="small" />
      </IconButton>

      <DialogContent sx={{ p: 0, position: 'relative', bgcolor: '#000000' }}>
        {showLogoReveal ? (
          <Box
            data-testid="limdb-logo-reveal"
            sx={{
              width: '100%',
              aspectRatio: '16/9',
              bgcolor: '#000000',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 2,
              position: 'relative',
              overflow: 'hidden',
              boxShadow: 'inset 0 0 100px rgba(229, 9, 20, 0.5)',
            }}
          >
            <Box
              sx={{
                animation: `${logoGlow} 2s infinite ease-in-out`,
                display: 'flex',
                justifyContent: 'center',
              }}
            >
              <LIMDbLogo width={280} height={70} />
            </Box>
            <Typography
              variant="subtitle1"
              sx={{
                fontWeight: 800,
                letterSpacing: 3,
                color: '#f5c518',
                textTransform: 'uppercase',
                textShadow: '0 2px 10px rgba(0,0,0,0.8)',
                mt: 1,
              }}
            >
              Now Showing
            </Typography>
          </Box>
        ) : (
          <Box
            sx={{
              position: 'relative',
              width: '100%',
              aspectRatio: '16/9',
              bgcolor: '#000000',
              overflow: 'hidden',
            }}
          >
            {/* 50% Transparent User-Oriented Subtitles at Top with No Border, No Background, No Icons */}
            {currentSubtitle && (
              <Box
                data-testid="standby-subtitle"
                sx={{
                  position: 'absolute',
                  top: 16,
                  left: '50%',
                  transform: 'translateX(-50%)',
                  maxWidth: '90%',
                  bgcolor: 'transparent',
                  border: 'none',
                  p: 0,
                  textAlign: 'center',
                  pointerEvents: 'none',
                  zIndex: 5,
                  animation: `${subtitleFade} 0.5s ease-in forwards`,
                }}
              >
                <Typography
                  variant="body1"
                  sx={{
                    color: '#ffffff',
                    opacity: 0.5,
                    fontWeight: 600,
                    fontSize: { xs: '0.85rem', sm: '1.05rem' },
                    textShadow: '0 2px 6px rgba(0, 0, 0, 0.95)',
                    letterSpacing: 0.5,
                  }}
                >
                  {currentSubtitle}
                </Typography>
              </Box>
            )}

            {/* Embedded 16:9 YouTube Trailer with seamless autoplay and loop */}
            <iframe
              data-testid="trailer-iframe"
              width="100%"
              height="100%"
              src={`https://www.youtube.com/embed/${trailerId}?autoplay=1&mute=1&controls=1&rel=0&modestbranding=1&loop=1&playlist=${trailerId}&enablejsapi=1`}
              title="LIMDb Pre-Show Trailer"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
              allowFullScreen
              style={{
                border: 'none',
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
              }}
            />
          </Box>
        )}
      </DialogContent>
    </Dialog>
  );
}

export default BackendStandbyModal;
