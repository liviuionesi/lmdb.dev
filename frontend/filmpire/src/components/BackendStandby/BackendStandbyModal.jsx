import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogActions,
  Box,
  Typography,
  Button,
  Chip,
  LinearProgress,
  IconButton,
  keyframes,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import TheatersIcon from '@mui/icons-material/Theaters';
import FastfoodIcon from '@mui/icons-material/Fastfood';
import MovieIcon from '@mui/icons-material/Movie';
import PlayCircleFilledWhiteIcon from '@mui/icons-material/PlayCircleFilledWhite';

import { useBackendWakeup } from './useBackendWakeup';

const rotateReel = keyframes`
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
`;

const pulseGlow = keyframes`
  0%, 100% {
    opacity: 0.8;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.05);
  }
`;

const slideCredits = keyframes`
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
`;

const CINEMA_STAGES = [
  {
    step: 1,
    act: 'ACT I: SCENE SETUP',
    title: 'Dimming the House Lights',
    description: 'Powering up the cloud projector, sound systems, and streaming servers...',
    icon: <TheatersIcon fontSize="medium" color="primary" />,
  },
  {
    step: 2,
    act: 'ACT II: COMING ATTRACTIONS',
    title: 'Rolling the Film Reels',
    description: 'Loading thousands of movies, cast details, trailers, and reviews into memory...',
    icon: <MovieIcon fontSize="medium" color="primary" />,
  },
  {
    step: 3,
    act: 'ACT III: CURTAIN CALL',
    title: 'Feature Presentation Starting',
    description: 'Grab your popcorn! The main feature is about to begin in just a few seconds...',
    icon: <FastfoodIcon fontSize="medium" color="primary" />,
  },
];

/**
 * Cinematic Movie Theater Standby & Auto-Wakeup Modal.
 *
 * <p>Delivers a cinema pre-show experience while the cloud backend boots up from standby,
 * featuring a theatrical countdown, marquee stages, rolling film credits, and automatic dismissal.
 *
 * @param {Object} props
 * @param {Function} [props.onBackendReady] - Callback triggered when the backend becomes live
 */
function BackendStandbyModal({ onBackendReady }) {
  const [open, setOpen] = useState(false);
  const [dismissed, setDismissed] = useState(false);

  const {
    status,
    secondsRemaining,
    progressPercentage,
    targetCloud,
    currentStep,
    wakeUp,
  } = useBackendWakeup({
    autoWakeup: true,
    onReady: () => {
      setOpen(false);
      if (onBackendReady) {
        onBackendReady();
      }
    },
  });

  useEffect(() => {
    if ((status === 'STANDBY' || status === 'WAKING_UP') && !dismissed) {
      setOpen(true);
    } else if (status === 'ONLINE' || status === 'READY') {
      setOpen(false);
    }
  }, [status, dismissed]);

  const handleClose = () => {
    setDismissed(true);
    setOpen(false);
  };

  const handleSwitchCloud = (cloud) => {
    wakeUp(cloud);
  };

  const currentStage = CINEMA_STAGES.find((s) => s.step === currentStep) || CINEMA_STAGES[0];

  if (!open) {
    return null;
  }

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      fullWidth
      maxWidth="xs"
      PaperProps={{
        sx: {
          borderRadius: 3,
          overflow: 'hidden',
          backgroundImage: 'none',
          boxShadow: (theme) => (theme.palette.mode === 'dark' ? '0 12px 40px rgba(0,0,0,0.8)' : '0 12px 40px rgba(0,0,0,0.15)'),
        },
      }}
    >
      <DialogContent sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 2.5, position: 'relative' }}>
        {/* Close Button */}
        <IconButton
          aria-label="close"
          onClick={handleClose}
          size="small"
          sx={{ position: 'absolute', top: 12, right: 12, color: 'text.secondary' }}
        >
          <CloseIcon fontSize="small" />
        </IconButton>

        {/* Theatrical Marquee Header */}
        <Box sx={{ textAlign: 'center', pt: 1 }}>
          <Box
            sx={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              p: 1.2,
              borderRadius: '50%',
              bgcolor: 'action.hover',
              mb: 1.5,
              animation: `${pulseGlow} 3s infinite ease-in-out`,
            }}
          >
            <TheatersIcon
              sx={{
                fontSize: 36,
                color: 'primary.main',
                animation: `${rotateReel} 12s linear infinite`,
              }}
            />
          </Box>
          <Typography
            variant="caption"
            sx={{
              display: 'block',
              letterSpacing: 2.5,
              fontWeight: 700,
              color: 'primary.main',
              textTransform: 'uppercase',
              mb: 0.5,
            }}
          >
            Filmpire Theaters • Pre-Show
          </Typography>
          <Typography variant="h5" fontWeight={800} sx={{ letterSpacing: -0.5 }}>
            Feature Presentation
          </Typography>
        </Box>

        {/* Countdown & Progress Card */}
        <Box
          sx={{
            bgcolor: 'action.hover',
            p: 2,
            borderRadius: 2,
            display: 'flex',
            flexDirection: 'column',
            gap: 1.5,
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <PlayCircleFilledWhiteIcon fontSize="small" color="primary" />
              <Typography variant="caption" fontWeight={700} color="text.secondary" sx={{ letterSpacing: 1 }}>
                {currentStage.act}
              </Typography>
            </Box>
            <Typography variant="subtitle2" fontWeight={800} color="primary.main">
              {`${secondsRemaining}s until showtime`}
            </Typography>
          </Box>

          <LinearProgress
            variant="determinate"
            value={progressPercentage}
            color="primary"
            sx={{ height: 6, borderRadius: 3 }}
          />

          {/* Animated Stage Credits Text */}
          <Box
            key={currentStep}
            sx={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: 1.5,
              pt: 0.5,
              animation: `${slideCredits} 0.4s ease-out`,
            }}
          >
            <Box sx={{ mt: 0.2 }}>{currentStage.icon}</Box>
            <Box>
              <Typography variant="subtitle2" fontWeight={700}>
                {currentStage.title}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ fontSize: '0.825rem', lineHeight: 1.4 }}>
                {currentStage.description}
              </Typography>
            </Box>
          </Box>
        </Box>

        {/* Cinema Screen / Cloud Selection Pills */}
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
          <Typography variant="caption" color="text.secondary" fontWeight={600}>
            Projection:
          </Typography>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Chip
              label="Screen 1: Azure AKS"
              color={targetCloud === 'azure' ? 'primary' : 'default'}
              variant={targetCloud === 'azure' ? 'filled' : 'outlined'}
              onClick={() => handleSwitchCloud('azure')}
              clickable
              size="small"
              sx={{ fontWeight: 600, fontSize: '0.75rem' }}
            />
            <Chip
              label="Screen 2: AWS EC2"
              color={targetCloud === 'aws' ? 'primary' : 'default'}
              variant={targetCloud === 'aws' ? 'filled' : 'outlined'}
              onClick={() => handleSwitchCloud('aws')}
              clickable
              size="small"
              sx={{ fontWeight: 600, fontSize: '0.75rem' }}
            />
          </Box>
        </Box>

        {/* Rolling Movie Credits Footer Ticker */}
        <Typography
          variant="caption"
          align="center"
          color="text.disabled"
          sx={{ fontStyle: 'italic', fontSize: '0.75rem', px: 1 }}
        >
          &quot;Starring Your Favorite Blockbusters • Sound by Dolby Cloud • Directed by Filmpire&quot;
        </Typography>
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2.5, pt: 0, justifyContent: 'space-between' }}>
        <Button onClick={handleClose} color="inherit" size="small">
          Browse Offline
        </Button>
        <Button
          onClick={() => wakeUp(targetCloud)}
          variant="contained"
          color="primary"
          size="small"
          sx={{ fontWeight: 700 }}
        >
          Restart Pre-Show
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default BackendStandbyModal;
