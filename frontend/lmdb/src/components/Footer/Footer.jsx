import React, { useEffect, useState } from 'react';
import { Box, Typography } from '@mui/material';
import { Link } from 'react-router-dom';
import useStyles from './styles';
import { resolveApiUrl } from '../../utils/apiUrl';

function Footer() {
  const { classes } = useStyles();
  const [providerLabel, setProviderLabel] = useState(null);
  const [status, setStatus] = useState('resolving'); // 'resolving' | 'up' | 'standby'

  useEffect(() => {
    let isMounted = true;
    (async () => {
      try {
        const baseUrl = await resolveApiUrl();
        if (!baseUrl) {
          if (isMounted) {
            setStatus('standby');
            setProviderLabel('Multi-Cloud Architecture (Standby)');
          }
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
          } else if (data.cloudProvider === 'aws') {
            setProviderLabel('Amazon Web Services (k3s)');
          } else if (data.cloudProvider === 'azure') {
            setProviderLabel('Microsoft Azure (AKS)');
          } else {
            setProviderLabel('Cloud Microservices');
          }
          setStatus('up');
        } else if (isMounted) {
          setStatus('standby');
          setProviderLabel('Multi-Cloud Architecture (Standby)');
        }
      } catch {
        if (isMounted) {
          setStatus('standby');
          setProviderLabel('Multi-Cloud Architecture (Standby)');
        }
      }
    })();

    return () => {
      isMounted = false;
    };
  }, []);

  const dotClass = status === 'up' ? classes.onlineDot : classes.standbyDot;
  const displayText = status === 'up'
    ? `Powered by ${providerLabel || 'Cloud Microservices'}`
    : (providerLabel || 'Backend in Standby');

  return (
    <footer className={classes.footerContainer}>
      <Box className={classes.statusBadge} aria-label="Backend status badge">
        <span className={`${classes.pulseDot} ${dotClass}`} />
        <Typography component="span" className={classes.providerText}>
          {displayText}
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
          <span>. This product uses the TMDB API but is not endorsed or certified by TMDB.</span>
        </Typography>
      </Box>

      <Typography className={classes.copyrightText}>
        © {new Date().getFullYear()} LMDB (Live Movies Database) • Multi-Cloud Architecture
      </Typography>
    </footer>
  );
}

export default Footer;
