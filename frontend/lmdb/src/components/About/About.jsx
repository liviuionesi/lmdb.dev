import React from 'react';
import {
  Box,
  Typography,
  Button,
  Divider,
  Chip,
  Link as MuiLink,
  Paper,
} from '@mui/material';
import LanguageIcon from '@mui/icons-material/Language';
import LinkedInIcon from '@mui/icons-material/LinkedIn';
import GitHubIcon from '@mui/icons-material/GitHub';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import StorageIcon from '@mui/icons-material/Storage';
import PsychologyIcon from '@mui/icons-material/Psychology';
import MicIcon from '@mui/icons-material/Mic';
import CloudQueueIcon from '@mui/icons-material/CloudQueue';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';

import useStyles from './styles';
import LMDBLogo from '../Logo/LMDBLogo';
import TMDBLogo from './TMDBLogo';

/**
 * About LMDB & Official TMDB Attribution Page.
 * Renders in the main content area with native theme styling.
 * Discloses brand identity, creator portfolio links (liviuionesi.com), and official TMDB data attribution.
 */
function About() {
  const { classes } = useStyles();

  return (
    <Box className={classes.container} data-testid="about-page">
      {/* Hero Header */}
      <Box className={classes.heroBox}>
        <LMDBLogo width={260} height={60} />
        <Typography variant="h4" component="h1" className={classes.heroTitle}>
          Live Movies Database
        </Typography>
        <Typography variant="body1" className={classes.heroSubtitle}>
          An enterprise-grade, event-driven cinema streaming and AI recommendation platform
          architected and engineered by <strong>Liviu Ionesi</strong>.
        </Typography>
      </Box>

      {/* Creator & Brand Showcase (liviuionesi.com) */}
      <Paper elevation={0} className={`${classes.sectionCard} ${classes.creatorCard}`}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1 }}>
          <VerifiedUserIcon color="primary" fontSize="large" />
          <Box>
            <Typography variant="h5" component="h2" fontWeight={800}>
              Architect & Engineering: Liviu Ionesi
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Founder & Software Architect • Cloud-Native Microservices Specialist
            </Typography>
          </Box>
        </Box>

        <Typography variant="body1" sx={{ mt: 2, lineHeight: 1.7 }}>
          <strong>LMDB</strong> represents both <em>Live Movies Database</em> and a creator namesake tribute for{' '}
          <strong>Liviu Ionesi</strong> (<em>LI Movies DB / Liviu&apos;s Movie Database</em>). Designed from the ground up
          to demonstrate modern production software engineering, LMDB operates an independent, polyglot microservices
          ecosystem with local AI inference, asynchronous event streaming, and zero-touch multi-cloud orchestration.
        </Typography>

        {/* Creator Brand Links */}
        <Box className={classes.buttonGroup}>
          <Button
            variant="contained"
            color="primary"
            startIcon={<LanguageIcon />}
            endIcon={<OpenInNewIcon fontSize="small" />}
            component="a"
            href="https://liviuionesi.com"
            target="_blank"
            rel="noopener noreferrer"
            data-testid="creator-website-link"
          >
            Visit LiviuIonesi.com
          </Button>

          <Button
            variant="outlined"
            color="primary"
            startIcon={<LinkedInIcon />}
            endIcon={<OpenInNewIcon fontSize="small" />}
            component="a"
            href="https://www.linkedin.com/in/liviuionesi/"
            target="_blank"
            rel="noopener noreferrer"
            data-testid="creator-linkedin-link"
          >
            Connect on LinkedIn
          </Button>

          <Button
            variant="outlined"
            color="inherit"
            startIcon={<GitHubIcon />}
            endIcon={<OpenInNewIcon fontSize="small" />}
            component="a"
            href="https://github.com/liviuionesi/lmdb.dev"
            target="_blank"
            rel="noopener noreferrer"
            data-testid="project-github-link"
          >
            GitHub Repository
          </Button>
        </Box>
      </Paper>

      {/* Official TMDB Data Source & Legal Compliance */}
      <Paper elevation={0} className={`${classes.sectionCard} ${classes.tmdbCard}`}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 1.5, mb: 1 }}>
          <Box>
            <Typography variant="h5" component="h2" fontWeight={800} color="primary.main">
              Data Source & Credits
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Powered by The Movie Database (TMDB) API v3
            </Typography>
          </Box>

          <MuiLink
            href="https://www.themoviedb.org/"
            target="_blank"
            rel="noopener noreferrer"
            sx={{ display: 'inline-flex', alignItems: 'center', gap: 1, textDecoration: 'none' }}
            aria-label="Visit The Movie Database official website"
          >
            <TMDBLogo width={100} height={24} />
            <OpenInNewIcon fontSize="small" sx={{ color: '#01b4e4' }} />
          </MuiLink>
        </Box>

        <Typography variant="body1" sx={{ mt: 1.5, lineHeight: 1.7 }}>
          Movie and TV metadata, synopses, actor filmographies, biographies, release schedules, genre classifications,
          and high-resolution artwork are sourced through{' '}
          <MuiLink href="https://www.themoviedb.org/" target="_blank" rel="noopener noreferrer" fontWeight={600}>
            The Movie Database (TMDB) public API
          </MuiLink>.
        </Typography>

        <Box className={classes.disclaimerBox}>
          <Typography variant="subtitle2" fontWeight={700} color="text.primary" gutterBottom>
            Official TMDB Terms of Service Notice:
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
            &quot;This product uses the TMDB API but is not endorsed or certified by TMDB.&quot;
          </Typography>
        </Box>
      </Paper>

      {/* System Architecture Highlights */}
      <Paper elevation={0} className={classes.sectionCard}>
        <Typography variant="h5" component="h2" fontWeight={800}>
          Architecture & Engineering Showcase
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          How LMDB transforms external cinema data into a self-healing, distributed platform
        </Typography>

        <Box className={classes.techGrid}>
          <Box className={classes.techItem}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
              <StorageIcon color="primary" />
              <Typography variant="subtitle1" fontWeight={700}>
                Polyglot Persistence & Facade
              </Typography>
            </Box>
            <Typography variant="body2" color="text.secondary">
              TMDB v3 facade maps external schemas to strongly-typed local MongoDB documents and caches hot queries
              in Redis, healing schema drift on read.
            </Typography>
          </Box>

          <Box className={classes.techItem}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
              <PsychologyIcon color="secondary" />
              <Typography variant="subtitle1" fontWeight={700}>
                Local AI & Vector Search ($0 Cost)
              </Typography>
            </Box>
            <Typography variant="body2" color="text.secondary">
              Natural language movie recommendations, chat, and semantic search powered by Spring AI, Ollama (LLaMA 3.2),
              and PostgreSQL with pgvector embeddings.
            </Typography>
          </Box>

          <Box className={classes.techItem}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
              <MicIcon color="error" />
              <Typography variant="subtitle1" fontWeight={700}>
                Embedded Voice Recognition
              </Typography>
            </Box>
            <Typography variant="body2" color="text.secondary">
              Speech-to-text navigation running entirely offline in container memory using an embedded Vosk C++ native model.
            </Typography>
          </Box>

          <Box className={classes.techItem}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
              <CloudQueueIcon color="primary" />
              <Typography variant="subtitle1" fontWeight={700}>
                Multi-Cloud Zero-Touch Parity
              </Typography>
            </Box>
            <Typography variant="body2" color="text.secondary">
              Codified infrastructure in Terraform and Kustomize with full parity across Azure AKS, AWS k3s on EC2, and Minikube.
            </Typography>
          </Box>
        </Box>

        <Divider sx={{ my: 2.5 }} />

        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
          <Chip label="Java 25 (Virtual Threads)" variant="outlined" size="small" />
          <Chip label="Spring Boot 4.1" variant="outlined" size="small" />
          <Chip label="Spring Cloud Gateway" variant="outlined" size="small" />
          <Chip label="React 18 & Vite" variant="outlined" size="small" />
          <Chip label="Redux Toolkit Query" variant="outlined" size="small" />
          <Chip label="PostgreSQL 17 + pgvector" variant="outlined" size="small" />
          <Chip label="MongoDB 8.0 & Redis 7.4" variant="outlined" size="small" />
          <Chip label="MinIO S3 Object Storage" variant="outlined" size="small" />
          <Chip label="Prometheus & Grafana" variant="outlined" size="small" />
        </Box>
      </Paper>
    </Box>
  );
}

export default About;
