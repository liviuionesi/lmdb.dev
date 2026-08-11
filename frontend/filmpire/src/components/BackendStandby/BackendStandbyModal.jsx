import React, { useState, useEffect, useMemo } from 'react';
import {
  Dialog,
  DialogContent,
  Box,
  Typography,
  Button,
  Chip,
  IconButton,
  TextField,
  InputAdornment,
  keyframes,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import PlayCircleFilledWhiteIcon from '@mui/icons-material/PlayCircleFilledWhite';
import FiberDvrIcon from '@mui/icons-material/FiberDvr';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import SearchIcon from '@mui/icons-material/Search';
import MovieFilterIcon from '@mui/icons-material/MovieFilter';

import { useBackendWakeup } from './useBackendWakeup';
import {
  CURATED_TRAILERS,
  getStandbyTrailerId,
  setStandbyTrailerId,
  extractYouTubeId,
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

const subtitleFade = keyframes`
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
`;

const SUBTITLES = [
  {
    minRemaining: 70,
    text: '🎬 Welcome to Filmpire Theaters! Starting the cloud backend for you...',
  },
  {
    minRemaining: 50,
    text: '⚡ Booting API Gateway microservices & allocating high-speed streaming routes...',
  },
  {
    minRemaining: 25,
    text: '🍿 Loading 10,000+ movie titles, cast profiles, reviews, and 4K posters...',
  },
  {
    minRemaining: 5,
    text: '🌟 Calibrating Dolby audio streams & warming up TMDB cache clusters...',
  },
  {
    minRemaining: 0,
    text: '✨ Backend is ready! The silver screen is about to light up...',
  },
];

/**
 * Cinematic Movie Trailer Standby Experience with Configurable YouTube Trailers,
 * Curated Presets, Dynamic Announcer Subtitles, and Filmpire Studio Logo Reveal.
 *
 * @param {Object} props
 * @param {Function} [props.onBackendReady] - Callback triggered when the backend is live
 */
function BackendStandbyModal({ onBackendReady }) {
  const [open, setOpen] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const [showLogoReveal, setShowLogoReveal] = useState(false);
  const [trailerId, setTrailerId] = useState(() => getStandbyTrailerId());
  const [customInput, setCustomInput] = useState('');
  const [showSearch, setShowSearch] = useState(false);

  const {
    status,
    secondsRemaining,
    targetCloud,
    wakeUp,
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

  const handleSwitchCloud = (cloud) => {
    wakeUp(cloud);
  };

  const handleSelectTrailer = (id) => {
    const extracted = extractYouTubeId(id);
    setTrailerId(extracted);
    setStandbyTrailerId(extracted);
  };

  const handleApplyCustomTrailer = (e) => {
    if (e) e.preventDefault();
    if (!customInput.trim()) return;
    handleSelectTrailer(customInput.trim());
    setCustomInput('');
    setShowSearch(false);
  };

  // Find active subtitle according to countdown time
  const currentSubtitle = useMemo(() => {
    const match = SUBTITLES.find((sub) => secondsRemaining >= sub.minRemaining);
    return match ? match.text : SUBTITLES[SUBTITLES.length - 1].text;
  }, [secondsRemaining]);

  // Format digital timecode
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
      maxWidth="md"
      PaperProps={{
        sx: {
          bgcolor: '#050508',
          color: '#ffffff',
          borderRadius: 3,
          border: '1px solid rgba(245, 197, 24, 0.3)',
          overflow: 'hidden',
          boxShadow: '0 25px 90px rgba(0, 0, 0, 0.95), 0 0 60px rgba(229, 9, 20, 0.35)',
          position: 'relative',
        },
      }}
    >
      <DialogContent
        sx={{
          p: { xs: 2, sm: 3 },
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
          position: 'relative',
        }}
      >
        {/* Top Header: Telemetry & Close */}
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
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
              label="STARTING SERVICE"
              sx={{
                bgcolor: 'rgba(229, 9, 20, 0.25)',
                color: '#ff4d58',
                border: '1px solid rgba(229, 9, 20, 0.4)',
                fontWeight: 700,
                fontSize: '0.68rem',
                letterSpacing: 1,
                height: 22,
              }}
            />
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="caption" sx={{ color: '#f5c518', fontWeight: 800, letterSpacing: 1 }}>
              {`${secondsRemaining}s TO SHOWTIME`}
            </Typography>
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

        {/* Central Stage: YouTube Trailer or Filmpire Logo Reveal */}
        {showLogoReveal ? (
          <Box
            data-testid="filmpire-logo-reveal"
            sx={{
              width: '100%',
              aspectRatio: '16/9',
              bgcolor: '#000000',
              borderRadius: 2,
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
              borderRadius: 2,
              overflow: 'hidden',
              bgcolor: '#000000',
              border: '1px solid rgba(255,255,255,0.1)',
              boxShadow: '0 10px 40px rgba(0,0,0,0.9)',
            }}
          >
            {/* Embedded 16:9 YouTube Trailer */}
            <iframe
              data-testid="trailer-iframe"
              width="100%"
              height="100%"
              src={`https://www.youtube-nocookie.com/embed/${trailerId}?autoplay=1&mute=0&controls=1&rel=0&modestbranding=1`}
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

            {/* Dynamic Announcer Subtitles Box */}
            <Box
              sx={{
                position: 'absolute',
                bottom: 8,
                left: '50%',
                transform: 'translateX(-50%)',
                width: '92%',
                bgcolor: 'rgba(0, 0, 0, 0.85)',
                border: '1px solid rgba(245, 197, 24, 0.35)',
                borderRadius: 1.5,
                px: 2,
                py: 1,
                textAlign: 'center',
                backdropFilter: 'blur(6px)',
                pointerEvents: 'none',
                animation: `${subtitleFade} 0.3s ease-out`,
                zIndex: 3,
              }}
            >
              <Typography
                variant="body2"
                sx={{
                  color: '#fff37a',
                  fontWeight: 700,
                  fontSize: { xs: '0.8rem', sm: '0.92rem' },
                  textShadow: '0 2px 4px rgba(0,0,0,0.9)',
                  letterSpacing: 0.3,
                }}
              >
                {currentSubtitle}
              </Typography>
            </Box>
          </Box>
        )}

        {/* Curated Trailer Selector & Search Ticker */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 1 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8 }}>
              <MovieFilterIcon sx={{ color: '#f5c518', fontSize: 18 }} />
              <Typography
                variant="caption"
                sx={{
                  color: 'rgba(255,255,255,0.7)',
                  fontWeight: 700,
                  letterSpacing: 1,
                  textTransform: 'uppercase',
                  fontSize: '0.72rem',
                }}
              >
                Trailer Playlist:
              </Typography>
            </Box>

            <Button
              size="small"
              onClick={() => setShowSearch((prev) => !prev)}
              startIcon={<SearchIcon fontSize="small" />}
              sx={{
                color: '#f5c518',
                fontSize: '0.75rem',
                fontWeight: 700,
                textTransform: 'none',
                p: 0.2,
                minWidth: 'auto',
              }}
            >
              {showSearch ? 'Close Search' : 'Custom Trailer URL / ID'}
            </Button>
          </Box>

          {/* Search / Custom YouTube URL Bar */}
          {showSearch && (
            <Box
              component="form"
              onSubmit={handleApplyCustomTrailer}
              sx={{ display: 'flex', gap: 1, pt: 0.5 }}
            >
              <TextField
                size="small"
                fullWidth
                placeholder="Paste YouTube Video Link or ID (e.g. https://youtu.be/...)"
                value={customInput}
                onChange={(e) => setCustomInput(e.target.value)}
                autoFocus
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon sx={{ color: '#f5c518', fontSize: 18 }} />
                    </InputAdornment>
                  ),
                  sx: {
                    bgcolor: 'rgba(255,255,255,0.06)',
                    color: '#ffffff',
                    fontSize: '0.8rem',
                    borderRadius: 1.5,
                  },
                }}
              />
              <Button
                type="submit"
                variant="contained"
                size="small"
                sx={{
                  bgcolor: '#f5c518',
                  color: '#000000',
                  fontWeight: 800,
                  fontSize: '0.75rem',
                  whiteSpace: 'nowrap',
                  '&:hover': { bgcolor: '#ffd54f' },
                }}
              >
                Load
              </Button>
            </Box>
          )}

          {/* Curated Presets Chips */}
          <Box sx={{ display: 'flex', gap: 0.8, overflowX: 'auto', py: 0.3 }}>
            {CURATED_TRAILERS.map((item) => (
              <Chip
                key={item.id}
                label={item.title}
                onClick={() => handleSelectTrailer(item.id)}
                clickable
                size="small"
                sx={{
                  bgcolor: trailerId === item.id ? 'rgba(245, 197, 24, 0.25)' : 'rgba(255,255,255,0.05)',
                  color: trailerId === item.id ? '#f5c518' : 'rgba(255,255,255,0.7)',
                  border: trailerId === item.id ? '1px solid #f5c518' : '1px solid rgba(255,255,255,0.1)',
                  fontWeight: 700,
                  fontSize: '0.72rem',
                  '&:hover': { bgcolor: 'rgba(245, 197, 24, 0.35)' },
                }}
              />
            ))}
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
            flexWrap: 'wrap',
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <PlayCircleFilledWhiteIcon sx={{ color: '#f5c518', fontSize: 20 }} />
            <Typography
              variant="caption"
              sx={{
                color: 'rgba(255,255,255,0.7)',
                fontWeight: 700,
                letterSpacing: 1,
                textTransform: 'uppercase',
                fontSize: '0.72rem',
              }}
            >
              Soundstage Screen:
            </Typography>
          </Box>

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
            Skip Trailer & Enter Offline
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
            Restart Pre-Show
          </Button>
        </Box>
      </DialogContent>
    </Dialog>
  );
}

export default BackendStandbyModal;
