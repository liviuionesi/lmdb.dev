import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  Box,
  Typography,
  Button,
  Chip,
  IconButton,
  keyframes,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import VolumeUpIcon from '@mui/icons-material/VolumeUp';
import VolumeOffIcon from '@mui/icons-material/VolumeOff';
import TheatersIcon from '@mui/icons-material/Theaters';
import MovieIcon from '@mui/icons-material/Movie';
import FastfoodIcon from '@mui/icons-material/Fastfood';
import FiberDvrIcon from '@mui/icons-material/FiberDvr';

import { useBackendWakeup } from './useBackendWakeup';
import { CinemaLeaderCanvas } from './CinemaLeaderCanvas';
import { useCinemaAudio } from './useCinemaAudio';

const sprocketScroll = keyframes`
  0% {
    background-position: 0 0;
  }
  100% {
    background-position: 0 40px;
  }
`;

const filmFlicker = keyframes`
  0%, 100% {
    opacity: 0.98;
  }
  50% {
    opacity: 1;
  }
  80% {
    opacity: 0.96;
  }
`;

const clapMotion = keyframes`
  0% {
    transform: rotate(-12deg);
  }
  20% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(0deg);
  }
`;

const equalizerPulse = keyframes`
  0%, 100% {
    height: 4px;
  }
  50% {
    height: 16px;
  }
`;

const CINEMA_ACTS = [
  {
    step: 1,
    act: 'ACT I // SCENE 01',
    label: 'THE PROJECTION BOOTH',
    title: 'Dimming the House Lights & Heating Xenon Lamps',
    description: 'Cloud compute nodes are warming up. Allocating streaming memory and high-speed network routes...',
    icon: <TheatersIcon sx={{ color: '#f5c518', fontSize: 24 }} />,
  },
  {
    step: 2,
    act: 'ACT II // SCENE 02',
    label: 'THE VAULT OF BLOCKBUSTERS',
    title: 'Rolling 35mm Celluloid Reels & Trailers',
    description: 'Spinning up Filmpire microservices, fetching 10,000+ movie titles, cast rosters, and 4K posters...',
    icon: <MovieIcon sx={{ color: '#f5c518', fontSize: 24 }} />,
  },
  {
    step: 3,
    act: 'ACT III // SCENE 03',
    label: 'CURTAIN CALL',
    title: 'Our Feature Presentation is About to Begin',
    description: 'Grab your popcorn! Sound calibration complete. The silver screen lights up in seconds...',
    icon: <FastfoodIcon sx={{ color: '#e50914', fontSize: 24 }} />,
  },
];

/**
 * Award-winning Cinematic Movie Theater Standby & Auto-Wakeup Experience.
 *
 * <p>Immerses visitors in a classic Hollywood 35mm film leader pre-show with real-time
 * canvas radar sweep, sprocket perforations, Web Audio projector hum, live telemetry,
 * and seamless auto-dismissal on backend readiness.
 *
 * @param {Object} props
 * @param {Function} [props.onBackendReady] - Callback triggered when the backend goes live
 */
function BackendStandbyModal({ onBackendReady }) {
  const [open, setOpen] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const { isPlaying, toggleAudio, stopAudio } = useCinemaAudio();

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
      stopAudio();
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
      stopAudio();
      setOpen(false);
    }
  }, [status, dismissed, stopAudio]);

  const handleClose = () => {
    stopAudio();
    setDismissed(true);
    setOpen(false);
  };

  const handleSwitchCloud = (cloud) => {
    wakeUp(cloud);
  };

  const currentAct = CINEMA_ACTS.find((a) => a.step === currentStep) || CINEMA_ACTS[0];

  // Format digital timecode MM:SS:FF
  const minutes = Math.floor(secondsRemaining / 60).toString().padStart(2, '0');
  const seconds = (secondsRemaining % 60).toString().padStart(2, '0');
  const frames = Math.floor((secondsRemaining * 24) % 24).toString().padStart(2, '0');
  const timecode = `00:${minutes}:${seconds}:${frames}`;

  if (!open) {
    return null;
  }

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      fullWidth
      maxWidth="sm"
      PaperProps={{
        sx: {
          bgcolor: '#0a0a0f',
          color: '#ffffff',
          borderRadius: 3,
          border: '1px solid rgba(245, 197, 24, 0.25)',
          overflow: 'hidden',
          boxShadow: '0 25px 80px rgba(0, 0, 0, 0.9), 0 0 50px rgba(229, 9, 20, 0.25)',
          position: 'relative',
          animation: `${filmFlicker} 0.15s infinite`,
        },
      }}
    >
      {/* 1. SMPTE Film Leader & Dust Canvas Background */}
      <CinemaLeaderCanvas
        secondsRemaining={secondsRemaining}
        progressPercentage={progressPercentage}
      />

      {/* 2. Left 35mm Film Sprocket Border */}
      <Box
        sx={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: 20,
          height: '100%',
          bgcolor: '#050508',
          borderRight: '1px solid rgba(255,255,255,0.1)',
          backgroundImage:
            'radial-gradient(ellipse at center, rgba(255,255,255,0.7) 0%, rgba(255,255,255,0.7) 40%, transparent 45%)',
          backgroundSize: '12px 20px',
          backgroundRepeat: 'repeat-y',
          animation: `${sprocketScroll} 1s linear infinite`,
          zIndex: 1,
          opacity: 0.6,
        }}
      />

      {/* 3. Right 35mm Film Sprocket Border */}
      <Box
        sx={{
          position: 'absolute',
          top: 0,
          right: 0,
          width: 20,
          height: '100%',
          bgcolor: '#050508',
          borderLeft: '1px solid rgba(255,255,255,0.1)',
          backgroundImage:
            'radial-gradient(ellipse at center, rgba(255,255,255,0.7) 0%, rgba(255,255,255,0.7) 40%, transparent 45%)',
          backgroundSize: '12px 20px',
          backgroundRepeat: 'repeat-y',
          animation: `${sprocketScroll} 1s linear infinite`,
          zIndex: 1,
          opacity: 0.6,
        }}
      />

      <DialogContent
        sx={{
          p: { xs: 2.5, sm: 3.5 },
          px: { xs: 4, sm: 5 },
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
          position: 'relative',
          zIndex: 2,
        }}
      >
        {/* Top Control Bar: Clapper Slate + Audio Synthesizer + Close */}
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pb: 0.5 }}>
          {/* Hollywood Clapperboard Slate */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Box
              sx={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 0.5,
                bgcolor: 'rgba(255,255,255,0.06)',
                border: '1px solid rgba(245, 197, 24, 0.3)',
                borderRadius: 1,
                px: 1.2,
                py: 0.4,
              }}
            >
              <FiberDvrIcon sx={{ color: '#e50914', fontSize: 18 }} />
              <Typography
                variant="caption"
                sx={{
                  fontFamily: 'monospace',
                  fontWeight: 700,
                  color: '#f5c518',
                  letterSpacing: 1.5,
                  fontSize: '0.75rem',
                }}
              >
                {`TC ${timecode}`}
              </Typography>
            </Box>

            <Chip
              size="small"
              label="ROLLING 35MM"
              sx={{
                bgcolor: 'rgba(229, 9, 20, 0.2)',
                color: '#ff4d58',
                border: '1px solid rgba(229, 9, 20, 0.4)',
                fontWeight: 700,
                fontSize: '0.65rem',
                letterSpacing: 1,
                height: 20,
              }}
            />
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            {/* Synthesizer Audio Toggle */}
            <IconButton
              size="small"
              onClick={toggleAudio}
              title={isPlaying ? 'Mute 35mm Projector Sound' : 'Play 35mm Projector Sound'}
              sx={{
                color: isPlaying ? '#f5c518' : 'rgba(255,255,255,0.5)',
                bgcolor: 'rgba(255,255,255,0.05)',
                border: '1px solid rgba(255,255,255,0.1)',
                '&:hover': { bgcolor: 'rgba(245,197,24,0.15)' },
              }}
            >
              {isPlaying ? <VolumeUpIcon fontSize="small" /> : <VolumeOffIcon fontSize="small" />}
            </IconButton>

            {/* Close Button */}
            <IconButton
              aria-label="close"
              onClick={handleClose}
              size="small"
              sx={{
                color: 'rgba(255,255,255,0.6)',
                bgcolor: 'rgba(255,255,255,0.05)',
                '&:hover': { bgcolor: 'rgba(255,255,255,0.15)', color: '#ffffff' },
              }}
            >
              <CloseIcon fontSize="small" />
            </IconButton>
          </Box>
        </Box>

        {/* Studio Marquee Header */}
        <Box sx={{ textAlign: 'center', py: 0.5 }}>
          {/* Animated Clapperboard Arm */}
          <Box
            sx={{
              display: 'inline-block',
              transformOrigin: 'bottom left',
              animation: `${clapMotion} 2s ease-in-out infinite`,
              mb: 0.5,
            }}
          >
            <Typography
              variant="caption"
              sx={{
                display: 'block',
                letterSpacing: 4,
                fontWeight: 800,
                color: '#f5c518',
                textTransform: 'uppercase',
                textShadow: '0 0 12px rgba(245, 197, 24, 0.5)',
                fontSize: '0.75rem',
              }}
            >
              ★ FILMPIRE STUDIOS PRESENTS ★
            </Typography>
          </Box>

          <Typography
            variant="h4"
            fontWeight={900}
            sx={{
              letterSpacing: -0.5,
              background: 'linear-gradient(180deg, #ffffff 0%, #dcdcdc 50%, #f5c518 100%)',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
              textShadow: '0 4px 20px rgba(0,0,0,0.8)',
              fontFamily: '"Outfit", "Inter", sans-serif',
            }}
          >
            Feature Presentation
          </Typography>
        </Box>

        {/* Central SMPTE Leader Countdown Display */}
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            py: 2,
            position: 'relative',
          }}
        >
          {/* Big Vintage Countdown Number */}
          <Typography
            variant="h1"
            fontWeight={900}
            sx={{
              fontSize: { xs: '4.5rem', sm: '5.5rem' },
              lineHeight: 1,
              fontFamily: '"Outfit", "Inter", monospace',
              color: '#ffffff',
              textShadow:
                '0 0 25px rgba(245, 197, 24, 0.8), 0 0 50px rgba(229, 9, 20, 0.5), 0 4px 10px rgba(0,0,0,0.9)',
              letterSpacing: -2,
            }}
          >
            {secondsRemaining}
          </Typography>

          <Typography
            variant="caption"
            sx={{
              letterSpacing: 2,
              color: 'rgba(255,255,255,0.7)',
              textTransform: 'uppercase',
              fontWeight: 700,
              mt: 0.5,
            }}
          >
            Seconds until showtime
          </Typography>
        </Box>

        {/* Theatrical Act & Scene Narrative Card */}
        <Box
          sx={{
            bgcolor: 'rgba(15, 15, 25, 0.85)',
            border: '1px solid rgba(245, 197, 24, 0.2)',
            borderRadius: 2,
            p: 2,
            backdropFilter: 'blur(8px)',
            boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.1)',
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography
                variant="caption"
                sx={{
                  fontFamily: 'monospace',
                  fontWeight: 800,
                  color: '#e50914',
                  letterSpacing: 1.5,
                  fontSize: '0.75rem',
                }}
              >
                {currentAct.act}
              </Typography>
              <Typography
                variant="caption"
                sx={{ color: 'rgba(255,255,255,0.4)', fontWeight: 600, fontSize: '0.7rem' }}
              >
                {`// ${currentAct.label}`}
              </Typography>
            </Box>

            {/* Animated Equalizer Sound Bars */}
            <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: 0.4, height: 16 }}>
              {[0, 0.2, 0.4, 0.1, 0.3].map((delay, idx) => (
                <Box
                  key={idx}
                  sx={{
                    width: 3,
                    bgcolor: '#f5c518',
                    borderRadius: 1,
                    animation: `${equalizerPulse} 0.8s ease-in-out infinite`,
                    animationDelay: `${delay}s`,
                  }}
                />
              ))}
            </Box>
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
            <Box sx={{ mt: 0.3, p: 0.8, borderRadius: 1.5, bgcolor: 'rgba(255,255,255,0.05)' }}>
              {currentAct.icon}
            </Box>
            <Box>
              <Typography variant="subtitle2" fontWeight={700} sx={{ color: '#ffffff', mb: 0.3 }}>
                {currentAct.title}
              </Typography>
              <Typography
                variant="body2"
                sx={{ color: 'rgba(255,255,255,0.7)', fontSize: '0.8rem', lineHeight: 1.4 }}
              >
                {currentAct.description}
              </Typography>
            </Box>
          </Box>
        </Box>

        {/* Projection Soundstage & Cloud Selection */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 1.5,
            pt: 0.5,
          }}
        >
          <Typography
            variant="caption"
            sx={{
              color: 'rgba(255,255,255,0.6)',
              fontWeight: 700,
              letterSpacing: 1,
              textTransform: 'uppercase',
              fontSize: '0.7rem',
            }}
          >
            Soundstage:
          </Typography>

          <Box sx={{ display: 'flex', gap: 0.8, flexWrap: 'wrap' }}>
            <Chip
              label="Screen 1: Azure AKS"
              onClick={() => handleSwitchCloud('azure')}
              clickable
              size="small"
              sx={{
                bgcolor: targetCloud === 'azure' ? 'rgba(245, 197, 24, 0.25)' : 'rgba(255,255,255,0.05)',
                color: targetCloud === 'azure' ? '#f5c518' : 'rgba(255,255,255,0.6)',
                border: targetCloud === 'azure' ? '1px solid #f5c518' : '1px solid rgba(255,255,255,0.1)',
                fontWeight: 700,
                fontSize: '0.72rem',
                '&:hover': { bgcolor: 'rgba(245, 197, 24, 0.35)' },
              }}
            />
            <Chip
              label="Screen 2: AWS EC2"
              onClick={() => handleSwitchCloud('aws')}
              clickable
              size="small"
              sx={{
                bgcolor: targetCloud === 'aws' ? 'rgba(229, 9, 20, 0.25)' : 'rgba(255,255,255,0.05)',
                color: targetCloud === 'aws' ? '#ff4d58' : 'rgba(255,255,255,0.6)',
                border: targetCloud === 'aws' ? '1px solid #e50914' : '1px solid rgba(255,255,255,0.1)',
                fontWeight: 700,
                fontSize: '0.72rem',
                '&:hover': { bgcolor: 'rgba(229, 9, 20, 0.35)' },
              }}
            />
            <Chip
              label="Screen 3: Minikube Tunnel"
              onClick={() => handleSwitchCloud('minikube')}
              clickable
              size="small"
              sx={{
                bgcolor: targetCloud === 'minikube' || targetCloud === 'tunnel' ? 'rgba(33, 150, 243, 0.25)' : 'rgba(255,255,255,0.05)',
                color: targetCloud === 'minikube' || targetCloud === 'tunnel' ? '#64b5f6' : 'rgba(255,255,255,0.6)',
                border: targetCloud === 'minikube' || targetCloud === 'tunnel' ? '1px solid #2196f3' : '1px solid rgba(255,255,255,0.1)',
                fontWeight: 700,
                fontSize: '0.72rem',
                '&:hover': { bgcolor: 'rgba(33, 150, 243, 0.35)' },
              }}
            />
          </Box>
        </Box>

        {/* Live Cinema Telemetry HUD Bar */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            bgcolor: 'rgba(0,0,0,0.5)',
            border: '1px solid rgba(255,255,255,0.08)',
            borderRadius: 1.5,
            px: 1.5,
            py: 0.6,
          }}
        >
          <Typography
            variant="caption"
            sx={{ fontFamily: 'monospace', color: '#f5c518', fontSize: '0.68rem', fontWeight: 600 }}
          >
            FPS: 24.00 (CINEMATIC)
          </Typography>
          <Typography
            variant="caption"
            sx={{ fontFamily: 'monospace', color: 'rgba(255,255,255,0.6)', fontSize: '0.68rem' }}
          >
            DCI 4K • 2.39:1 SCOPE
          </Typography>
          <Typography
            variant="caption"
            sx={{ fontFamily: 'monospace', color: '#ff4d58', fontSize: '0.68rem', fontWeight: 600 }}
          >
            {targetCloud === 'minikube' || targetCloud === 'tunnel' ? 'LOCAL_MINIKUBE_TUNNEL' : `${targetCloud.toUpperCase()}_CLUSTER`}
          </Typography>
        </Box>

        {/* Action Controls */}
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pt: 0.5 }}>
          <Button
            onClick={handleClose}
            size="small"
            sx={{
              color: 'rgba(255,255,255,0.6)',
              fontSize: '0.8rem',
              '&:hover': { color: '#ffffff' },
            }}
          >
            Skip Pre-Show (Offline)
          </Button>

          <Button
            onClick={() => wakeUp(targetCloud)}
            variant="contained"
            size="small"
            sx={{
              bgcolor: '#e50914',
              color: '#ffffff',
              fontWeight: 800,
              letterSpacing: 0.5,
              fontSize: '0.8rem',
              boxShadow: '0 4px 15px rgba(229, 9, 20, 0.4)',
              '&:hover': { bgcolor: '#b80710' },
            }}
          >
            Restart Projector
          </Button>
        </Box>
      </DialogContent>
    </Dialog>
  );
}

export default BackendStandbyModal;
