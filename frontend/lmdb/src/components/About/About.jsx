import React from 'react';
import {
  Box,
  Typography,
  Button,
  Link as MuiLink,
  Paper,
} from '@mui/material';
import LanguageIcon from '@mui/icons-material/Language';
import LinkedInIcon from '@mui/icons-material/LinkedIn';
import GitHubIcon from '@mui/icons-material/GitHub';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import SearchIcon from '@mui/icons-material/Search';
import MicIcon from '@mui/icons-material/Mic';
import RecommendIcon from '@mui/icons-material/Recommend';

import useStyles from './styles';
import LMDBLogo from '../Logo/LMDBLogo';
import TMDBLogo from './TMDBLogo';

/**
 * About LMDB & Official TMDB Attribution Page.
 * Displays a brief how-to guide, creator links, and TMDB attribution.
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
          Your intelligent cinema streaming and AI recommendation platform.
        </Typography>
      </Box>

      {/* How to Use Section */}
      <Paper elevation={0} className={classes.sectionCard}>
        <Box sx={{ mb: 3 }}>
          <Typography variant="h5" component="h2" fontWeight={800} gutterBottom>
            How to Use LMDB
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Welcome to LMDB! Here is a brief guide on how to get the most out of the platform.
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', gap: 2, mb: 3 }}>
          <SearchIcon color="primary" sx={{ fontSize: 32 }} />
          <Box>
            <Typography variant="h6" fontWeight={700}>Search & Semantic Queries</Typography>
            <Typography variant="body1" sx={{ mt: 0.5 }}>
              Use the search bar at the top of the screen to find specific movies, actors, or directors.
              You can also use natural language to ask questions or give commands, such as <em>&quot;Show me the best action movies from 2020.&quot;</em>
            </Typography>
          </Box>
        </Box>

        <Box sx={{ display: 'flex', gap: 2, mb: 3 }}>
          <MicIcon color="error" sx={{ fontSize: 32 }} />
          <Box>
            <Typography variant="h6" fontWeight={700}>Voice Dictation</Typography>
            <Typography variant="body1" sx={{ mt: 0.5 }}>
              Click the microphone icon inside the search bar to dictate your search query or command instead of typing.
              It will transcribe your speech directly into the search box.
            </Typography>
          </Box>
        </Box>

        <Box sx={{ display: 'flex', gap: 2 }}>
          <RecommendIcon color="secondary" sx={{ fontSize: 32 }} />
          <Box>
            <Typography variant="h6" fontWeight={700}>Personalized Recommendations</Typography>
            <Typography variant="body1" sx={{ mt: 0.5 }}>
              Sign in to save your favorite movies, add titles to your watchlist, and receive personalized
              AI-driven recommendations based on your unique tastes.
            </Typography>
          </Box>
        </Box>
      </Paper>

      {/* Creator Links */}
      <Paper elevation={0} className={`${classes.sectionCard} ${classes.creatorCard}`}>
        <Typography variant="h6" component="h3" fontWeight={700} gutterBottom>
          Created by Liviu Ionesi
        </Typography>
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

      {/* Official TMDB Data Source */}
      <Paper elevation={0} className={`${classes.sectionCard} ${classes.tmdbCard}`}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 1.5, mb: 1 }}>
          <Box>
            <Typography variant="h6" component="h3" fontWeight={700} color="primary.main">
              Data Source & Credits
            </Typography>
          </Box>
          <MuiLink href="https://www.themoviedb.org/" target="_blank" rel="noopener noreferrer">
            <TMDBLogo width={100} height={24} />
          </MuiLink>
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          This product uses the TMDB API but is not endorsed or certified by TMDB.
        </Typography>
      </Paper>
    </Box>
  );
}

export default About;
