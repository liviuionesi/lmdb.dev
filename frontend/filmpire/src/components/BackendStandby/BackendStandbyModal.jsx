import React, { useState, useEffect, useMemo } from 'react';
import {
  Dialog,
  DialogContent,
  Box,
  Typography,
  IconButton,
  keyframes,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';

import { useBackendWakeup } from './useBackendWakeup';
import {
  getStandbyTrailerId,
} from '../../utils/trailer';

const FILMPIRE_LOGO_RED = 'https://fontmeme.com/permalink/210930/8531c658a743debe1e1aa1a2fc82006e.png';

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

const bannerFade = keyframes`
  from {
    opacity: 0;
    transform: translate(-50%, -8px);
  }
  to {
    opacity: 1;
    transform: translate(-50%, 0);
  }
`;

const SUBTITLES = [
  {
    minRemaining: 70,
    text: '🎬 Welcome to Filmpire Theaters! Starting the cloud backend for you...',
  },
  {
    minRemaining: 50,
    text: '⚡ Booting API Gateway microservices & allocating high-speed routes...',
  },
  {
    minRemaining: 25,
    text: '🍿 Loading 10,000+ movie titles, cast profiles, reviews, and 4K posters...',
  },
  {
    minRemaining: 1,
    text: '🌟 Calibrating Dolby audio streams & warming up TMDB cache clusters...',
  },
  {
    minRemaining: 0,
    text: '⏳ Microservices are performing final health checks & warming up... Almost ready!',
  },
];

/**
 * Minimalist, Immersive Cinema Trailer Standby Experience.
 * Features top-positioned subtitles that never overlap YouTube player controls or native CC.
 *
 * @param {Object} props
 * @param {Function} [props.onBackendReady] - Callback triggered when the backend is live
 */
function BackendStandbyModal({ onBackendReady }) {
  const [open, setOpen] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const [showLogoReveal, setShowLogoReveal] = useState(false);
  const [trailerId] = useState(() => getStandbyTrailerId());

  const {
    status,
    secondsRemaining,
  } = useBackendWakeup({
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

  const handleClose = () => {
    setDismissed(true);
    setOpen(false);
    setShowLogoReveal(false);
  };

  // Find active announcer subtitle according to countdown
  const currentSubtitle = useMemo(() => {
    const match = SUBTITLES.find((sub) => secondsRemaining >= sub.minRemaining);
    return match ? match.text : SUBTITLES[SUBTITLES.length - 1].text;
  }, [secondsRemaining]);

  if (!open) {
    return null;
  }

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      fullWidth
      maxWidth="md"
      PaperProps={{
        sx: {
          bgcolor: '#000000',
          color: '#ffffff',
          borderRadius: 3,
          border: '1px solid rgba(245, 197, 24, 0.25)',
          overflow: 'hidden',
          boxShadow: '0 25px 90px rgba(0, 0, 0, 0.98), 0 0 60px rgba(229, 9, 20, 0.35)',
          position: 'relative',
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
            data-testid="filmpire-logo-reveal"
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
              component="img"
              src={FILMPIRE_LOGO_RED}
              alt="Filmpire Logo Reveal"
              sx={{
                width: { xs: '65%', sm: '48%' },
                maxWidth: 380,
                animation: `${logoGlow} 2s infinite ease-in-out`,
              }}
            />
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1 }}>
              <CheckCircleIcon sx={{ color: '#4caf50', fontSize: 24 }} />
              <Typography
                variant="subtitle1"
                sx={{
                  fontWeight: 800,
                  letterSpacing: 2,
                  color: '#f5c518',
                  textTransform: 'uppercase',
                  textShadow: '0 2px 10px rgba(0,0,0,0.8)',
                }}
              >
                Backend Online • Rolling Feature
              </Typography>
            </Box>
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
            {/* Top Announcer Subtitle Banner (positioned at top to avoid overlapping YouTube CC) */}
            <Box
              sx={{
                position: 'absolute',
                top: 14,
                left: '50%',
                transform: 'translateX(-50%)',
                maxWidth: '85%',
                bgcolor: 'rgba(0, 0, 0, 0.82)',
                border: '1px solid rgba(245, 197, 24, 0.4)',
                borderRadius: 2,
                px: { xs: 1.5, sm: 2.5 },
                py: { xs: 0.6, sm: 0.9 },
                textAlign: 'center',
                backdropFilter: 'blur(8px)',
                pointerEvents: 'none',
                animation: `${bannerFade} 0.3s ease-out`,
                zIndex: 5,
                boxShadow: '0 4px 20px rgba(0, 0, 0, 0.8)',
              }}
            >
              <Typography
                variant="body2"
                sx={{
                  color: '#fff37a',
                  fontWeight: 700,
                  fontSize: { xs: '0.8rem', sm: '0.94rem' },
                  textShadow: '0 2px 5px rgba(0,0,0,0.95)',
                  letterSpacing: 0.3,
                }}
              >
                {currentSubtitle}
              </Typography>
            </Box>

            {/* Embedded 16:9 YouTube Trailer with seamless loop */}
            <iframe
              data-testid="trailer-iframe"
              width="100%"
              height="100%"
              src={`https://www.youtube-nocookie.com/embed/${trailerId}?autoplay=1&mute=0&controls=1&rel=0&modestbranding=1&loop=1&playlist=${trailerId}`}
              title="Filmpire Pre-Show Trailer"
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
