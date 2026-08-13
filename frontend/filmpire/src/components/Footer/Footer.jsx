import React, { useEffect, useState } from 'react';
import { Box, Typography } from '@mui/material';
import { Link } from 'react-router-dom';
import useStyles from './styles';
import { resolveApiUrl } from '../../utils/apiUrl';

function Footer() {
  const { classes } = useStyles();
  const [providerLabel, setProviderLabel] = useState('Microsoft Azure (AKS)');
  const [status, setStatus] = useState('up'); // 'up' | 'standby'

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

      {/* TMDB TOS Compliance & About Page Navigation */}
      <Box className={classes.attributionSection}>
        <Link to="/about" className={classes.creditsLink} data-testid="about-credits-link">
          About LMDB & TMDB Credits
        </Link>
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
    </footer>
  );
}

export default Footer;
