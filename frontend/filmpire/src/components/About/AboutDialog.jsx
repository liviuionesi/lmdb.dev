import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Divider,
  Chip,
  Link as MuiLink,
} from '@mui/material';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import InfoIcon from '@mui/icons-material/Info';
import MemoryIcon from '@mui/icons-material/Memory';
import CloudQueueIcon from '@mui/icons-material/CloudQueue';

import TMDBLogo from './TMDBLogo';
import LMDBLogo from '../Logo/LMDBLogo';

/**
 * About LMDB & Official TMDB Attribution Modal.
 * Transparently discloses the platform's origin, architecture, and official TMDB API credits.
 *
 * @param {Object} props
 * @param {boolean} props.open
 * @param {Function} props.onClose
 */
function AboutDialog({ open, onClose }) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="about-dialog-title"
      data-testid="about-dialog"
    >
      <DialogTitle id="about-dialog-title" sx={{ pb: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
        <InfoIcon color="primary" />
        <Typography variant="h6" component="span" fontWeight={700}>
          About LMDB & Data Attribution
        </Typography>
      </DialogTitle>

      <DialogContent dividers sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
        {/* LMDB Branding Header */}
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', py: 1, gap: 1 }}>
          <LMDBLogo width={220} height={50} />
          <Typography variant="body2" color="text.secondary" textAlign="center">
            <strong>Live Movies Database</strong> (also known as <em>LI Movies DB / Liviu's Movie Database</em>)
            <br />
            An enterprise-grade, event-driven cinema streaming and AI recommendation platform.
          </Typography>
        </Box>

        <Divider />

        {/* Official TMDB Attribution & Credits */}
        <Box
          sx={{
            p: 2,
            bgcolor: (theme) => (theme.palette.mode === 'dark' ? 'rgba(1, 180, 228, 0.08)' : 'rgba(1, 180, 228, 0.06)'),
            borderRadius: 2,
            border: '1px solid rgba(1, 180, 228, 0.25)',
            display: 'flex',
            flexDirection: 'column',
            gap: 1.5,
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 1 }}>
            <Typography variant="subtitle1" fontWeight={700} color="primary.main">
              Data Source & Credits
            </Typography>
            <MuiLink
              href="https://www.themoviedb.org/"
              target="_blank"
              rel="noopener noreferrer"
              sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, textDecoration: 'none' }}
              aria-label="Visit The Movie Database official website"
            >
              <TMDBLogo width={80} height={20} />
              <OpenInNewIcon fontSize="small" sx={{ color: '#01b4e4' }} />
            </MuiLink>
          </Box>

          <Typography variant="body2" color="text.primary">
            Movie metadata, actor filmographies, plot overviews, release details, and poster artwork are provided by{' '}
            <MuiLink href="https://www.themoviedb.org/" target="_blank" rel="noopener noreferrer" fontWeight={600}>
              The Movie Database (TMDB) API
            </MuiLink>.
          </Typography>

          <Box
            sx={{
              p: 1.5,
              bgcolor: (theme) => (theme.palette.mode === 'dark' ? 'rgba(0, 0, 0, 0.3)' : 'rgba(255, 255, 255, 0.8)'),
              borderRadius: 1,
              borderLeft: '4px solid #01b4e4',
            }}
          >
            <Typography variant="caption" display="block" color="text.secondary" sx={{ fontStyle: 'italic' }}>
              "This product uses the TMDB API but is not endorsed or certified by TMDB."
            </Typography>
          </Box>
        </Box>

        {/* Architecture & Engineering Highlights */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          <Typography variant="subtitle2" fontWeight={700}>
            Independent Microservices Architecture:
          </Typography>
          <Typography variant="body2" color="text.secondary">
            LMDB is not a simple proxy — it runs a distributed, polyglot microservices backend architected by{' '}
            <strong>Liviu Ionesi</strong> with local catalog persistence and self-healing schema synchronization:
          </Typography>

          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.8, pt: 0.5 }}>
            <Chip size="small" icon={<MemoryIcon />} label="Java 25 & Spring Boot 4" variant="outlined" />
            <Chip size="small" icon={<MemoryIcon />} label="Spring AI & pgvector (Local LLM)" variant="outlined" />
            <Chip size="small" icon={<MemoryIcon />} label="Vosk Offline Speech-to-Text" variant="outlined" />
            <Chip size="small" icon={<CloudQueueIcon />} label="Azure AKS & AWS k3s" variant="outlined" />
            <Chip size="small" icon={<CloudQueueIcon />} label="React 18 & Redux Toolkit" variant="outlined" />
          </Box>
        </Box>
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} variant="contained" color="primary">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default AboutDialog;
