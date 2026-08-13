import React, { useEffect, useState } from 'react';
import { Box, Typography } from '@mui/material';
import useStyles from './styles';
import { resolveApiUrl } from '../../utils/apiUrl';
import AboutDialog from '../About/AboutDialog';

function Footer() {
  const { classes } = useStyles();
  const [providerLabel, setProviderLabel] = useState('Microsoft Azure (AKS)');
  const [status, setStatus] = useState('up'); // 'up' | 'standby'
  const [aboutOpen, setAboutOpen] = useState(false);

  useEffect(() => {
    // Resolve once on mount without periodic polling
    let isMounted = true;
    (async () => {
      try {
        const baseUrl = await resolveApiUrl();
        if (!baseUrl) {
          if (isMounted) setStatus('standby');
          return;
        }
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 2500);
        const res = await fetch(`${baseUrl}/actuator/activity`, {
          signal: controller.signal,
          cache: 'no-store',
        });
        clearTimeout(timeoutId);

        if (res.ok && isMounted) {
          const data = await res.json();
          if (data.cloudProviderLabel) {
            setProviderLabel(data.cloudProviderLabel);
          }
          setStatus('up');
        } else if (isMounted) {
          setStatus('standby');
        }
      } catch {
        if (isMounted) setStatus('standby');
      }
    })();

    return () => {
      isMounted = false;
    };
  }, []);

  const dotClass = status === 'up' ? classes.onlineDot : classes.standbyDot;

  return (
    <footer className={classes.footerContainer}>
      <Box className={classes.statusBadge} aria-label="Backend status badge">
        <span className={`${classes.pulseDot} ${dotClass}`} />
        <Typography component="span" className={classes.providerText}>
          Powered by {providerLabel}
        </Typography>
      </Box>

      {/* TMDB TOS Compliance & About Dialog Trigger */}
      <Box className={classes.attributionSection}>
        <button
          type="button"
          className={classes.creditsLink}
          onClick={() => setAboutOpen(true)}
          data-testid="about-credits-button"
        >
          About LMDB & TMDB Credits
        </button>
        <Typography className={classes.disclaimerText}>
          Movie data and imagery provided by{' '}
          <a
            href="https://www.themoviedb.org/"
            target="_blank"
            rel="noopener noreferrer"
            style={{ color: 'inherit', fontWeight: 600 }}
          >
            TMDB
          </a>
          . This product uses the TMDB API but is not endorsed or certified by TMDB.
        </Typography>
      </Box>

      <Typography className={classes.copyrightText}>
        © {new Date().getFullYear()} LMDB (Live Movies Database) • Multi-Cloud Architecture
      </Typography>

      <AboutDialog open={aboutOpen} onClose={() => setAboutOpen(false)} />
    </footer>
  );
}

export default Footer;
